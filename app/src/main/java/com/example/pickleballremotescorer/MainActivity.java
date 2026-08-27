package com.example.pickleballremotescorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends Activity {

    private TextView scoreA, scoreB, serveA, serveB, status;
    private int a = 0, b = 0;
    private int servingTeam = 0; // 0 = Team A, 1 = Team B
    private int server = 1;
    private boolean gameOver = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pendingSingle = false;
    private boolean longTriggered = false;
    private long keyDownAt = 0L;

    private static final long DOUBLE_PRESS_MS = 430;
    private static final long LONG_PRESS_MS = 850;

    private final Deque<State> history = new ArrayDeque<>();

    private static class State {
        int a, b, team, server;
        boolean gameOver;
        State(int a, int b, int team, int server, boolean gameOver) {
            this.a=a; this.b=b; this.team=team; this.server=server; this.gameOver=gameOver;
        }
    }

    private final Runnable singlePress = new Runnable() {
        @Override public void run() {
            if (!pendingSingle || longTriggered) return;
            pendingSingle = false;
            if (gameOver) {
                startNewGame();
                return;
            }
            saveState();
            addPoint();
        }
    };

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (keyDownAt == 0L) return;
            longTriggered = true;
            pendingSingle = false;
            handler.removeCallbacks(singlePress);
            undo();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scoreA = findViewById(R.id.scoreA);
        scoreB = findViewById(R.id.scoreB);
        serveA = findViewById(R.id.serveA);
        serveB = findViewById(R.id.serveB);
        status = findViewById(R.id.status);

        // Score panels are display-only. Points can only be added with this button or remote.
        findViewById(R.id.pointButton).setOnClickListener(v -> {
            if (gameOver) {
                startNewGame();
            } else {
                saveState();
                addPoint();
            }
        });

        findViewById(R.id.undo).setOnClickListener(v -> undo());
        findViewById(R.id.switchServer).setOnClickListener(v -> {
            if (!gameOver) { saveState(); nextServer(); }
        });
        findViewById(R.id.reset).setOnClickListener(v -> confirmReset());

        updateDisplay();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean remoteKey =
                code == KeyEvent.KEYCODE_VOLUME_UP ||
                code == KeyEvent.KEYCODE_VOLUME_DOWN ||
                code == KeyEvent.KEYCODE_CAMERA ||
                code == KeyEvent.KEYCODE_ENTER;

        if (!remoteKey) return super.dispatchKeyEvent(event);

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                keyDownAt = System.currentTimeMillis();
                longTriggered = false;
                handler.postDelayed(longPress, LONG_PRESS_MS);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            keyDownAt = 0L;

            if (longTriggered) {
                longTriggered = false;
                return true;
            }

            if (gameOver) {
                pendingSingle = true;
                handler.removeCallbacks(singlePress);
                handler.postDelayed(singlePress, DOUBLE_PRESS_MS);
                return true;
            }

            if (pendingSingle) {
                pendingSingle = false;
                handler.removeCallbacks(singlePress);
                saveState();
                nextServer();
            } else {
                pendingSingle = true;
                handler.postDelayed(singlePress, DOUBLE_PRESS_MS);
            }
            return true;
        }
        return true;
    }

    private void saveState() {
        history.push(new State(a,b,servingTeam,server,gameOver));
    }

    private void undo() {
        if (history.isEmpty()) return;
        State s = history.pop();
        a=s.a; b=s.b; servingTeam=s.team; server=s.server; gameOver=s.gameOver;
        updateDisplay();
    }

    private void addPoint() {
        if (servingTeam == 0) a++; else b++;
        updateDisplay();
        checkWinner();
    }

    private void nextServer() {
        if (server == 1) {
            server = 2;
        } else {
            server = 1;
            servingTeam = 1 - servingTeam;
        }
        updateDisplay();
    }

    private void checkWinner() {
        if ((a >= 11 || b >= 11) && Math.abs(a-b) >= 2) {
            gameOver = true;
            updateDisplay();
        }
    }

    private void startNewGame() {
        a=0; b=0; servingTeam=0; server=1; gameOver=false;
        history.clear();
        pendingSingle=false;
        longTriggered=false;
        updateDisplay();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset game?")
                .setMessage("The score will return to 0–0.")
                .setPositiveButton("Reset",(d,w)->startNewGame())
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void updateDisplay() {
        scoreA.setText(String.valueOf(a));
        scoreB.setText(String.valueOf(b));

        if (gameOver) {
            serveA.setText(a>b ? "WINNER" : "");
            serveB.setText(b>a ? "WINNER" : "");
            status.setText((a>b ? "TEAM A" : "TEAM B") + " WINS  •  Press POINT or remote once for new game");
            return;
        }

        serveA.setText(servingTeam==0 ? "●  SERVING  •  SERVER " + server : "WAITING");
        serveB.setText(servingTeam==1 ? "●  SERVING  •  SERVER " + server : "WAITING");
        status.setText("FIRST TO 11  •  WIN BY 2");
    }
}
