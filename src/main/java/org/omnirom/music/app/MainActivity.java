package org.omnirom.music.app;

import android.app.ActionBar;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import com.commonsware.cwac.mediarouter.MediaRouteActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import org.omnirom.music.app.fragments.AutomixFragment;
import org.omnirom.music.app.fragments.DspProvidersFragment;
import org.omnirom.music.app.fragments.ListenNowFragment;
import org.omnirom.music.app.fragments.MySongsFragment;
import org.omnirom.music.app.fragments.NavigationDrawerFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.RecognitionFragment;
import org.omnirom.music.app.ui.PlayingBarView;
import org.omnirom.music.framework.CastModule;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackService;

public class MainActivity extends FragmentActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String TAG = "MainActivity";

    public static final int SECTION_LISTEN_NOW = 1;
    public static final int SECTION_MY_SONGS   = 2;
    public static final int SECTION_PLAYLISTS  = 3;
    public static final int SECTION_AUTOMIX    = 4;
    public static final int SECTION_RECOGNITION= 5;
    public static final int SECTION_NOW_PLAYING= 6;

    public static final int SECTION_DSP_EFFECTS= -1;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    private PlayingBarView mPlayingBarLayout;

    private boolean mRestoreBarOnBack;

    private CastModule mCastModule;

    private Handler mHandler;

    private int mCurrentFragmentIndex;

    private MenuItem mOfflineMenuItem;


    public MainActivity() {
        mHandler = new Handler();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout), getTheme());


        // Setup the playing bar click listener
        mPlayingBarLayout = (PlayingBarView) findViewById(R.id.playingBarLayout);
        mPlayingBarLayout.setWrapped(true, false);

        // Control MUSIC volume with the volume buttons by default now
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Setup Cast button
        mCastModule = new CastModule(this);
    }

    public boolean isPlayBarVisible() {
        return mPlayingBarLayout.isVisible();
    }

    @Override
    public void onBackPressed() {
        if (!mPlayingBarLayout.isWrapped()) {
            mPlayingBarLayout.setWrapped(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PluginsLookup.getDefault().connectPlayback();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        mCastModule.onStart();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mCastModule.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }

        // Release services connections if playback isn't happening
        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        try {
            if (!playbackService.isPlaying()) {
                PluginsLookup.getDefault().tearDown();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot determine if playbackservice is running");
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Reload the current fragment for layout changes
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                onNavigationDrawerItemSelected(mCurrentFragmentIndex);
            }
        });
    }

    @Override
    public boolean onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        boolean result = true;
        try {
            Fragment newFrag = null;
            mCurrentFragmentIndex = position;
            switch (position+1) {
                case SECTION_LISTEN_NOW:
                    newFrag = ListenNowFragment.newInstance();
                    break;
                case SECTION_PLAYLISTS:
                    newFrag = PlaylistListFragment.newInstance(true);
                    break;
                case SECTION_MY_SONGS:
                    newFrag = MySongsFragment.newInstance();
                    break;
                case SECTION_AUTOMIX:
                    newFrag = AutomixFragment.newInstance();
                    break;
                case SECTION_RECOGNITION:
                    newFrag = RecognitionFragment.newInstance();
                    break;
                case SECTION_NOW_PLAYING:
                    startActivity(new Intent(this, PlaybackQueueActivity.class));
                    break;
            }

            if (newFrag != null) {
                showFragment(newFrag, false);
                result = true;
            } else {
                result = false;
            }
        } catch (IllegalStateException e) {
            // The app is pausing
        }

        return result;
    }

    public void showFragment(Fragment f, boolean addToStack) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0 && !addToStack) {
            fragmentManager.popBackStack();
            if (mRestoreBarOnBack) {
                mRestoreBarOnBack = false;
            }
        }

        FragmentTransaction ft = fragmentManager.beginTransaction();
        if (addToStack) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            ft.addToBackStack(f.toString());
        } else {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        ft.replace(R.id.container, f);
        ft.commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case SECTION_LISTEN_NOW:
                mTitle = getString(R.string.title_section_listen_now);
                break;
            case SECTION_MY_SONGS:
                mTitle = getString(R.string.title_section_my_songs);
                break;
            case SECTION_PLAYLISTS:
                mTitle = getString(R.string.title_section_playlists);
                break;
            case SECTION_AUTOMIX:
                mTitle = getString(R.string.title_section_automix);
                break;
            case SECTION_RECOGNITION:
                mTitle = getString(R.string.title_section_recognition);
                break;
            case SECTION_DSP_EFFECTS:
                mTitle = getString(R.string.settings_dsp_config_title);
                break;
        }
    }

    public void setContentShadowTop(final int pxTop) {
        View actionBarShadow = findViewById(R.id.action_bar_shadow);
        if (actionBarShadow != null) {
            actionBarShadow.setTranslationY(pxTop);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setContentShadowTop(pxTop);
                }
            });
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();

        if (actionBar != null) {
            ((TextView) actionBar.getCustomView().findViewById(android.R.id.title)).setText(mTitle);
        }
    }

    public void toggleOfflineMode() {
        mOfflineMenuItem.setChecked(!mOfflineMenuItem.isChecked());
        ProviderAggregator.getDefault().notifyOfflineMode(mOfflineMenuItem.isChecked());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

            SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                    .getActionView();
            searchView.setSearchableInfo(searchManager
                    .getSearchableInfo(getComponentName()));

            int searchTextViewId = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
            TextView searchTextView = (TextView) searchView.findViewById(searchTextViewId);
            searchTextView.setHintTextColor(getResources().getColor(R.color.white));

            // Google, why is searchView using a Gingerbread-era enforced icon?
            int searchImgId = getResources().getIdentifier("android:id/search_button", null, null);
            ImageView v = (ImageView) searchView.findViewById(searchImgId);
            v.setImageResource(R.drawable.ic_action_search);

            // Setup cast button on 4.2+
            MenuItem castMenu = menu.findItem(R.id.action_cast);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                Log.d(TAG, "Showing cast action");
                MediaRouteActionProvider mediaRouteActionProvider =
                        (MediaRouteActionProvider) castMenu.getActionProvider();
                mediaRouteActionProvider.setRouteSelector(mCastModule.getSelector());
                castMenu.setVisible(true);
            } else {
                Log.w(TAG, "Api too low to show cast action");
                castMenu.setVisible(false);
            }

            // Offline mode
            mOfflineMenuItem = menu.findItem(R.id.action_offline_mode);
            mOfflineMenuItem.setChecked(ProviderAggregator.getDefault().isOfflineMode());

            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;

            case R.id.action_sound_effects:
                showFragment(new DspProvidersFragment(), true);
                break;

            case R.id.action_offline_mode:
                toggleOfflineMode();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
