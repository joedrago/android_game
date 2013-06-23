package com.jdrago.game;

import android.app.Activity;
import android.os.Bundle;

public class GameActivity extends Activity
{
    private GameView view_;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);

        getActionBar().hide();

        view_ = new GameView(getApplication());
        setContentView(view_);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        view_.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        view_.onResume();
    }
}
