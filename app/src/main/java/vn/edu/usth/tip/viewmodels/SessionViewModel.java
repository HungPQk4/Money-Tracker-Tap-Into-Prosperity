package vn.edu.usth.tip.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import vn.edu.usth.tip.network.responses.SessionResponse;
import vn.edu.usth.tip.repositories.SessionRepository;

public class SessionViewModel extends AndroidViewModel {

    private final SessionRepository repository;
    private final MutableLiveData<List<SessionResponse>> sessions = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public SessionViewModel(@NonNull Application application) {
        super(application);
        repository = new SessionRepository(application);
    }

    public LiveData<List<SessionResponse>> getSessions() { return sessions; }
    public LiveData<String> getError() { return error; }

    public void refresh() {
        repository.loadSessions(sessions, error);
    }

    public void revoke(String sessionId) {
        repository.revoke(sessionId, this::refresh, error);
    }

    public void logoutOthers() {
        repository.logoutOthers(this::refresh, error);
    }
}
