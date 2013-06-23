package com.jdrago.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameView extends GLSurfaceView
{
    private TestGame game_;

    public GameView(Context context)
    {
        super(context);
        setEGLContextClientVersion(2);
        game_ = new TestGame(context);
        setRenderer(game_);
    }

    public boolean onTouchEvent(MotionEvent event)
    {
        game_.onTouch((int)event.getX(0), (int)event.getY(0), event.getActionMasked() == MotionEvent.ACTION_DOWN);
        return true;
    }
}
