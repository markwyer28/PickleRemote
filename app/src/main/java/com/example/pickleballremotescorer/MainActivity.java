package com.example.pickleballremotescorer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends Activity {

    private TextView scoreA, scoreB, serveA, serveB, status;

    private int a = 0;
    private int b = 0;
    private int servingTeam = 0; // 0 = Team A, 1 = Team B
    private int server = 1;      // 1 or 2
    private boolean gameOver = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pendingSingle = false;
    private boolean longTriggered = false;
    private long keyDownAt = 0L;

    private static final long DOUBLE_PRESS_MS = 430;
    private static final long LONG_PRESS_MS = 850;

    private final Deque<State> history = new ArrayDeque<>();

    private static class State {
        int a, b, servingTeam, server;
        boolean gameOver;

        State(int a, int b, int servingTeam, int server, boolean gameOver) {
            this.a = a;
            this.b = b;
            this.servingTeam = servingTeam;
            this.server = server;
            this.gameOver = gameOver;
        }
    }

    // Single press is delayed briefly so we can tell it apart from a double press.
    private final Runnable singlePress = new Runnable() {
        @Override
        public void run() {
            if (!pendingSingle || longTriggered) return;

            pendingSingle = false;

            // After the game has finished, one short press starts the next game.
            if (gameOver) {
                startNewGame();
                return;
            }

            saveState();
            addPoint();
        }
    };

    // Long press undoes the last completed scoring/server action.
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (keyDownAt == 0L) return;

            longTriggered = true;
            pendingSingle = false;
            handler.removeCallbacks(singlePress);
            undo();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scoreA = findViewById(R.id.scoreA);
        scoreB = findViewById(R.id.scoreB);
        serveA = findViewById(R.id.serveA);
        serveB = findViewById(R.id.serveB);
        status = findViewById(R.id.status);

        findViewById(R.id.undo).setOnClickListener(v -> undo());

        findViewById(R.id.switchServer).setOnClickListener(v -> {
            if (!gameOver) {
                saveState();
                nextServer();
            }
        });

        findViewById(R.id.switchTeam).setOnClickListener(v -> {
            if (!gameOver) {
                saveState();
                servingTeam = 1 - servingTeam;
                server = 1;
                updateDisplay();
            }
        });

        findViewById(R.id.reset).setOnClickListener(v -> confirmReset());

        // Manual score correction remains available by tapping a team's score panel.
        findViewById(R.id.teamABox).setOnClickListener(v -> {
            if (!gameOver) {
                saveState();
                a++;
                updateDisplay();
                checkWinner();
            }
        });

        findViewById(R.id.teamBBox).setOnClickListener(v -> {
            if (!gameOver) {
                saveState();
                b++;
                updateDisplay();
                checkWinner();
            }
        });

        updateDisplay();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        boolean remoteKey =
                code == KeyEvent.KEYCODE_VOLUME_UP ||
                code == KeyEvent.KEYCODE_VOLUME_DOWN ||
                code == KeyEvent.KEYCODE_CAMERA ||
                code == KeyEvent.KEYCODE_ENTER;

        if (!remoteKey) {
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                keyDownAt = System.currentTimeMillis();
                longTriggered = false;
                handler.postDelayed(longPress, LONG_PRESS_MS);
            }
            return true; // Stop Android changing the volume.
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(longPress);
            keyDownAt = 0L;

            if (longTriggered) {
                longTriggered = false;
                return true;
            }

            // Once a game is over, any normal single press starts a clean new game.
            // We still use the normal short delay to distinguish it from a long press.
            if (gameOver) {
                pendingSingle = true;
                handler.removeCallbacks(singlePress);
                handler.postDelayed(singlePress, DOUBLE_PRESS_MS);
                return true;
            }

            if (pendingSingle) {
                // Second short press: this is a double press.
                // Cancel the pending point and move to the next server.
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
        history.push(new State(a, b, servingTeam, server, gameOver));
    }

    private void undo() {
        if (history.isEmpty()) return;

        State previous = history.pop();
        a = previous.a;
        b = previous.b;
        servingTeam = previous.servingTeam;
        server = previous.server;
        gameOver = previous.gameOver;

        updateDisplay();
    }

    private void addPoint() {
        // Traditional side-out pickleball: only the serving team scores.
        if (servingTeam == 0) {
            a++;
        } else {
            b++;
        }

        updateDisplay();
        checkWinner();
    }

    private void nextServer() {
        /*
         Doubles rotation:
           Team A Server 1
           Team A Server 2
           Team B Server 1
           Team B Server 2
           Team A Server 1
           ...
        */
        if (server == 1) {
            server = 2;
        } else {
            server = 1;
            servingTeam = 1 - servingTeam;
        }

        updateDisplay();
    }

    private void checkWinner() {
        if ((a >= 11 || b >= 11) && Math.abs(a - b) >= 2) {
            gameOver = true;
            updateDisplay();
        }
    }

    private void startNewGame() {
        a = 0;
        b = 0;
        servingTeam = 0;
        server = 1;
        gameOver = false;
        history.clear();
        pendingSingle = false;
        longTriggered = false;

        updateDisplay();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Reset game?")
                .setMessage("The score will return to 0–0.")
                .setPositiveButton("Reset", (dialog, which) -> startNewGame())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateDisplay() {
        scoreA.setText(String.valueOf(a));
        scoreB.setText(String.valueOf(b));

        if (gameOver) {
            String winner = a > b ? "TEAM A WINS" : "TEAM B WINS";
            serveA.setText("");
            serveB.setText("");
            status.setText(winner + " • Press remote once for new game");
            return;
        }

        if (servingTeam == 0) {
            serveA.setText("SERVING • SERVER " + server);
            serveB.setText("");
        } else {
            serveA.setText("");
            serveB.setText("SERVING • SERVER " + server);
        }

        status.setText(
                "Serving: Team " + (servingTeam == 0 ? "A" : "B") +
                " • Server " + server
        );
    }
}
