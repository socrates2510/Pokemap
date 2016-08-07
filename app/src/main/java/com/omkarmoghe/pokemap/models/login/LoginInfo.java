package com.omkarmoghe.pokemap.models.login;

import com.wanderingcan.pokegoapi.auth.CredentialProvider;

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;

/**
 * Created by chris on 7/25/2016.
 */

public abstract class LoginInfo extends CredentialProvider {

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_PTC = "ptc";

    private String mToken;

    public LoginInfo(String token){
        mToken = token;
    }

    public void setToken(String token) {
        this.mToken = token;
    }

    @Override
    public String getTokenId(){
        return mToken;
    }

    public abstract String getProvider();

    @Override
    public AuthInfo getAuthInfo(){
        AuthInfo.Builder builder = AuthInfo.newBuilder();
        builder.setProvider(getProvider());
        builder.setToken(AuthInfo.JWT.newBuilder().setContents(mToken).setUnknown2(59).build());
        return builder.build();
    }

    @Override
    public boolean isTokenIdExpired() {
        return false;
    }
}
