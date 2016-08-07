package com.omkarmoghe.pokemap.controllers.net;

import android.app.Activity;
import android.os.HandlerThread;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.omkarmoghe.pokemap.models.events.CatchablePokemonEvent;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.omkarmoghe.pokemap.models.events.GymsEvent;
import com.omkarmoghe.pokemap.models.events.InternalExceptionEvent;
import com.omkarmoghe.pokemap.models.events.LoginEventResult;
import com.omkarmoghe.pokemap.models.events.LurePokemonEvent;
import com.omkarmoghe.pokemap.models.events.PokestopsEvent;
import com.omkarmoghe.pokemap.models.events.ServerUnreachableEvent;
import com.omkarmoghe.pokemap.models.login.LoginInfo;
import com.wanderingcan.pokegoapi.api.PokemonGo;
import com.wanderingcan.pokegoapi.api.map.fort.Pokestop;
import com.wanderingcan.pokegoapi.api.map.pokemon.CatchablePokemon;
import com.wanderingcan.pokegoapi.exceptions.LoginFailedException;
import com.wanderingcan.pokegoapi.exceptions.RemoteServerException;


import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import POGOProtos.Map.Fort.FortDataOuterClass;
import POGOProtos.Map.Fort.FortLureInfoOuterClass;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.wanderingcan.pokegoapi.auth.CredentialProvider.PtcProviderConstants.CLIENT_ID;
import static com.wanderingcan.pokegoapi.auth.CredentialProvider.PtcProviderConstants.CLIENT_SECRET;
import static com.wanderingcan.pokegoapi.auth.CredentialProvider.PtcProviderConstants.LOGIN_OAUTH;
import static com.wanderingcan.pokegoapi.auth.CredentialProvider.PtcProviderConstants.LOGIN_URL;
import static com.wanderingcan.pokegoapi.auth.CredentialProvider.PtcProviderConstants.REDIRECT_URI;


/**
 * Created by vanshilshah on 20/07/16.
 */
public class NianticManager {
    private static final String TAG = "NianticManager";

    private static final String BASE_URL = "https://sso.pokemon.com/sso/";

    private static final NianticManager instance = new NianticManager();

    private List<NianticService> mNianticService;
    private final List<OkHttpClient> mClient;
    private Handler mHandler;

    private final List<OkHttpClient> mPoGoClient;
    private List<PokemonGo> mPokemonGo;


    private int pokemonFound = 0;
    private int currentScan = 0;
    private int pendingSearch = 0;

    private int currentBatchCall = 0;

    private int currentThread = 0;
    public int nianticThreads = 10;

    public static NianticManager getInstance(){
        return instance;
    }

    private NianticManager(){
        mPoGoClient = new ArrayList<>();
        mPokemonGo = new ArrayList<>();
        mClient = new ArrayList<>();
        mNianticService = new ArrayList<>();

        for (int i = 0; i < nianticThreads; i++) {
            mPoGoClient.add(new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build());
        }

        HandlerThread thread = new HandlerThread("Niantic Manager Thread");
        thread.start();
        mHandler = new Handler(thread.getLooper());



        for (int i = 0; i < nianticThreads; i++) {
        /*
		This is a temporary, in-memory cookie jar.
		We don't require any persistence outside of the scope of the login,
		so it being discarded is completely fine
		*/
            CookieJar tempJar = new CookieJar() {
                private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

                @Override
                public void saveFromResponse(okhttp3.HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url.host(), cookies);
                }

                @Override
                public List<Cookie> loadForRequest(okhttp3.HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host());
                    return cookies != null ? cookies : new ArrayList<Cookie>();
                }
            };

            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            OkHttpClient tmpClient = new OkHttpClient.Builder()
                    .cookieJar(tempJar)
                    .addInterceptor(new NetworkRequestLoggingInterceptor())
                    .build();
            mClient.add(tmpClient);

            NianticService tmpNianticService = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(tmpClient)
                    .build()
                    .create(NianticService.class);
            mNianticService.add(tmpNianticService);
        }
    }

    public void login(final String username, final String password, final LoginListener loginListener, final int currentThread){
        Callback<NianticService.LoginValues> valuesCallback = new Callback<NianticService.LoginValues>() {
            @Override
            public void onResponse(Call<NianticService.LoginValues> call, Response<NianticService.LoginValues> response) {
                if(response.body() != null) {
                    loginPTC(username, password, response.body(), loginListener, currentThread);
                } else {
                    Log.e(TAG, "PTC login failed via login(). There was no response.body().");
                    loginListener.authFailed("Fetching Pokemon Trainer Club's Login Url Values Failed");
                }

            }

            @Override
            public void onFailure(Call<NianticService.LoginValues> call, Throwable t) {
                t.printStackTrace();
                Log.e(TAG, "PTC login failed via login(). valuesCallback.onFailure() threw: " + t.getMessage());
                loginListener.authFailed("Fetching Pokemon Trainer Club's Login Url Values Failed");
            }
        };
        Call<NianticService.LoginValues> call = mNianticService.get(currentThread).getLoginValues();
        call.enqueue(valuesCallback);
    }

    private void loginPTC(final String username, final String password, NianticService.LoginValues values, final LoginListener loginListener, final int currentThread){
        HttpUrl url = HttpUrl.parse(LOGIN_URL).newBuilder()
                .addQueryParameter("lt", values.getLt())
                .addQueryParameter("execution", values.getExecution())
                .addQueryParameter("_eventId", "submit")
                .addQueryParameter("username", username)
                .addQueryParameter("password", password)
                .build();

        OkHttpClient client = mClient.get(currentThread).newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        NianticService service = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(NianticService.class);

        Callback<NianticService.LoginResponse> loginCallback = new Callback<NianticService.LoginResponse>() {
            @Override
            public void onResponse(Call<NianticService.LoginResponse> call, Response<NianticService.LoginResponse> response) {
                String location = response.headers().get("location");
                if (location != null && location.split("ticket=").length > 0) {
                    String ticket = location.split("ticket=")[1];
                    requestToken(ticket, loginListener, currentThread);
                } else {
                    Log.e(TAG, "PTC login failed via loginPTC(). There was no location header in response.");
                    loginListener.authFailed("Pokemon Trainer Club Login Failed");
                }
            }

            @Override
            public void onFailure(Call<NianticService.LoginResponse> call, Throwable t) {
                t.printStackTrace();
                Log.e(TAG, "PTC login failed via loginPTC(). loginCallback.onFailure() threw: " + t.getMessage());
                loginListener.authFailed("Pokemon Trainer Club Login Failed");
            }
        };
        Call<NianticService.LoginResponse> call = service.login(url.toString());
        call.enqueue(loginCallback);
    }

    private void requestToken(String code, final LoginListener loginListener, final int currentThread){
        Log.d(TAG, "requestToken() called with: code = [" + code + "]");
        HttpUrl url = HttpUrl.parse(LOGIN_OAUTH).newBuilder()
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("client_secret", CLIENT_SECRET)
                .addQueryParameter("grant_type", "refresh_token")
                .addQueryParameter("code", code)
                .build();

        Callback<ResponseBody> authCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String token = response.body().string().split("token=")[1];

                    if (token != null) {
                        token = token.split("&")[0];

                        loginListener.authSuccessful(token);
                    } else {
                        Log.e(TAG, "PTC login failed while fetching a requestToken via requestToken(). Token is null.");
                        loginListener.authFailed("Pokemon Trainer Club Login Failed");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "PTC login failed while fetching a requestToken authCallback.onResponse() raised: " + e.getMessage());
                    loginListener.authFailed("Pokemon Trainer Club Authentication Failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                Log.e(TAG, "PTC login failed while fetching a requestToken authCallback.onResponse() threw: " + t.getMessage());
                loginListener.authFailed("Pokemon Trainer Club Authentication Failed");
            }
        };
        Call<ResponseBody> call = mNianticService.get(currentThread).requestToken(url.toString());
        call.enqueue(authCallback);
    }

    public int getPokemonFound() {
        return pokemonFound;
    }

    public void setPokemonFound(int pokemonFound) {
        this.pokemonFound = pokemonFound;
    }

    public int getCurrentScan() {
        return currentScan;
    }

    public int getPendingSearch() {
        return pendingSearch;
    }

    public interface LoginListener {
        void authSuccessful(String authToken);
        void authFailed(String message);
    }

    public interface AuthListener{
        void authSuccessful();
        void authFailed(String message, String Provider);
    }

    /**
     * Sets the google auth token for the auth info also invokes the onLogin callback.
     */
    public void setLoginInfo(@NonNull final LoginInfo info) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
            try {
                int currentApi = currentThread % nianticThreads;
                Thread.sleep(133);
                PokemonGo pokemonGo = new PokemonGo(info, mPoGoClient.get(currentApi));
                mPokemonGo.add(pokemonGo);
                EventBus.getDefault().post(new LoginEventResult(true, info, mPokemonGo.get(currentApi)));
                currentThread++;
            } catch (InterruptedException | RemoteServerException | LoginFailedException | RuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "Setting google auth token failed. setGoogleAuthToken() raised: " + e.getMessage());
                EventBus.getDefault().post(new LoginEventResult(false, null, null));
            }
            }
        });
    }

    /**
     * Sets the pokemon trainer club auth token for the auth info also invokes the onLogin callback.
     */
    public void setLoginInfo(final Activity activity, @NonNull final LoginInfo info, @NonNull final AuthListener listener) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    int currentApi = currentThread % nianticThreads;
                    Thread.sleep(133);
                    PokemonGo pokemonGo = new PokemonGo(info, mPoGoClient.get(currentApi));
                    mPokemonGo.add(pokemonGo);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.authSuccessful();
                        }
                    });
                } catch (InterruptedException | RemoteServerException | LoginFailedException | RuntimeException e){
                    e.printStackTrace();
                    Log.e(TAG, "Failed to login using PoGoAPI via login(). Raised: " + e.getMessage());
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.authFailed(e.getMessage(), info.getProvider());
                        }
                    });

                }
            }
        });
    }

    public void getCatchablePokemon(final double lat, final double longitude, final double alt){
        final int myCurrentBatch = this.currentBatchCall;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    PokemonGo currentApi = mPokemonGo.get(currentThread%nianticThreads);
                    Log.d(TAG, "run: Min Refresh = " + currentApi.getSettings().getMapSettings().getMinRefresh());
                    Log.d(TAG, "run: Distance = " + currentApi.getSettings().getMapSettings().getEncoungerRange());
                    currentThread++;
                    if (currentApi != null && NianticManager.this.currentBatchCall == myCurrentBatch) {
                        Thread.sleep(133);
                        currentApi.setLocation(lat, longitude, alt);
                        Thread.sleep(5000 / NianticManager.this.nianticThreads);
                        List<CatchablePokemon> catchablePokemons = currentApi.Map.getCatchablePokemon();
                        if (NianticManager.this.currentBatchCall == myCurrentBatch) EventBus.getDefault().post(new CatchablePokemonEvent(catchablePokemons, lat, longitude));
                    }

                } catch (LoginFailedException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getCatchablePokemon(). Login credentials wrong or user banned. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new LoginEventResult(false, null, null));
                } catch (RemoteServerException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getCatchablePokemon(). Remote server unreachable. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new ServerUnreachableEvent(e));
                } catch (InterruptedException | RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getCatchablePokemon(). PoGoAPI crashed. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new InternalExceptionEvent(e));
                }
                NianticManager.this.currentScan++;
            }
        });
        this.pendingSearch++;
    }

    public void getLuredPokemon(final double lat, final double longitude, final double alt){
        final int myCurrentBatch = this.currentBatchCall;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {

                    PokemonGo currentApi = mPokemonGo.get(currentThread%nianticThreads);
                    currentThread++;
                    if (currentApi != null && NianticManager.this.currentBatchCall == myCurrentBatch) {
                        Thread.sleep(133);
                        currentApi.setLocation(lat, longitude, alt);
                        Thread.sleep(5000 / NianticManager.this.nianticThreads);

                        List<CatchablePokemon> pokemon = new ArrayList<>();
                        for(Pokestop pokestop: currentApi.Map.getMapObjects().getPokestops()){
                            if(!pokestop.getFortData().getLureInfo().equals(FortLureInfoOuterClass.FortLureInfo.getDefaultInstance())){
                                Log.d(TAG, "run: hasFortInfo = " + pokestop.getFortData().getLureInfo());
                                pokemon.add(new CatchablePokemon(pokestop.getFortData()));
                            }
                        }
                        if (NianticManager.this.currentBatchCall == myCurrentBatch) EventBus.getDefault().post(new LurePokemonEvent(pokemon, lat, longitude));
                    }

                } catch (LoginFailedException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). Login credentials wrong or user banned. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new LoginEventResult(false, null, null));
                } catch (RemoteServerException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). Remote server unreachable. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new ServerUnreachableEvent(e));
                } catch (InterruptedException | RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). PoGoAPI crashed. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new InternalExceptionEvent(e));
                }
            }
        });
    }


    public void getPokeStops(final double lat, final double longitude, final double alt){
        final int myCurrentBatch = this.currentBatchCall;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {

                    PokemonGo currentApi = mPokemonGo.get(currentThread%nianticThreads);
                    currentThread++;
                    if (currentApi != null && NianticManager.this.currentBatchCall == myCurrentBatch) {
                        Thread.sleep(133);
                        currentApi.setLocation(lat, longitude, alt);
                        Thread.sleep(5000 / NianticManager.this.nianticThreads);

                        Collection<Pokestop> pokestops = currentApi.Map.getMapObjects().getPokestops();

                        if (NianticManager.this.currentBatchCall == myCurrentBatch) EventBus.getDefault().post(new PokestopsEvent(pokestops, lat, longitude));
                    }

                } catch (LoginFailedException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). Login credentials wrong or user banned. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new LoginEventResult(false, null, null));
                } catch (RemoteServerException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). Remote server unreachable. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new ServerUnreachableEvent(e));
                } catch (InterruptedException | RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to fetch map information via getPokeStops(). PoGoAPI crashed. Raised: " + e.getMessage());
                    EventBus.getDefault().post(new InternalExceptionEvent(e));
                }
            }
        });
    }

    public void getGyms(final double latitude, final double longitude, final double alt) {

        final int myCurrentBatch = this.currentBatchCall;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
            try {

                PokemonGo currentApi = mPokemonGo.get(currentThread%nianticThreads);
                currentThread++;
                if (currentApi != null && NianticManager.this.currentBatchCall == myCurrentBatch) {
                    Thread.sleep(133);
                    currentApi.setLocation(latitude, longitude, alt);
                    Thread.sleep(5000 / NianticManager.this.nianticThreads);
                    Collection<FortDataOuterClass.FortData> gyms = currentApi.Map.getMapObjects().getGyms();
                    if (NianticManager.this.currentBatchCall == myCurrentBatch) EventBus.getDefault().post(new GymsEvent(gyms, latitude, longitude));
                }

            } catch (LoginFailedException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to fetch map information via getGyms(). Login credentials wrong or user banned. Raised: " + e.getMessage());
                EventBus.getDefault().post(new LoginEventResult(false, null, null));
            } catch (RemoteServerException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to fetch map information via getGyms(). Remote server unreachable. Raised: " + e.getMessage());
                EventBus.getDefault().post(new ServerUnreachableEvent(e));
            } catch (InterruptedException | RuntimeException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to fetch map information via getGyms(). PoGoAPI crashed. Raised: " + e.getMessage());
                EventBus.getDefault().post(new InternalExceptionEvent(e));
            }
            }
        });
    }

    public void resetSearchCount() {
        this.pendingSearch = 0;
        this.currentScan = 0;
        this.pokemonFound = 0;
        this.currentBatchCall++;
    }

    public void cancelPendingSearches(){
        this.currentBatchCall++;
    }
}
