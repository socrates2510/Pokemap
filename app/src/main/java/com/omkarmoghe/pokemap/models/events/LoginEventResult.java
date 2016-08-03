package com.omkarmoghe.pokemap.models.events;


import com.omkarmoghe.pokemap.models.login.LoginInfo;
import com.wanderingcan.pokegoapi.api.PokemonGo;

import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;

/**
 * Created by Jon on 7/23/2016.
 */
public class LoginEventResult implements IEvent {

    private boolean loggedIn;
    private LoginInfo authInfo;
    private PokemonGo pokemonGo;

    public LoginEventResult(boolean loggedIn, LoginInfo authInfo, PokemonGo pokemonGo) {
        this.loggedIn = loggedIn;
        this.authInfo = authInfo;
        this.pokemonGo = pokemonGo;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public LoginInfo getAuthInfo() {
        return authInfo;
    }

    public void setAuthInfo(LoginInfo authInfo) {
        this.authInfo = authInfo;
    }

    public PokemonGo getPokemonGo() {
        return pokemonGo;
    }

    public void setPokemonGo(PokemonGo pokemonGo) {
        this.pokemonGo = pokemonGo;
    }
}
