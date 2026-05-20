package vn.edu.usth.tip.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class SessionManager {
    private static volatile SessionManager instance;
    private final MutableLiveData<Boolean> sessionExpired = new MutableLiveData<>(false);

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) instance = new SessionManager();
            }
        }
        return instance;
    }

    public LiveData<Boolean> getSessionExpiredEvent() { return sessionExpired; }

    public void triggerSessionExpired() { sessionExpired.postValue(true); }

    public void clearSessionExpired() { sessionExpired.postValue(false); }
}
