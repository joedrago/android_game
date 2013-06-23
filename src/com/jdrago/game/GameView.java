package com.jdrago.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameView extends GLSurfaceView
{
    private Game game_;

    public GameView(Context context)
    {
        super(context);
        setEGLContextClientVersion(2);
        game_ = new Game(context);
        setRenderer(game_);
    }

    public boolean onTouchEvent(MotionEvent event)
    {
//        if(event.getActionMasked() == MotionEvent.ACTION_DOWN)
//        {
//            renderer_.click((int)event.getX(0), (int)event.getY(0));
//        }
        return true;
    }
}
