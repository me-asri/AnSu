package github.me_asri.ansu;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class AnsuApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
