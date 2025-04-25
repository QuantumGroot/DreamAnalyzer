package com.dashstudio.dreamanalyzer.ui.browse;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class BrowseViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public BrowseViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is dashboard fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}