package github.me_asri.ansu;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.sfuhrm.sudoku.Creator;
import de.sfuhrm.sudoku.GameMatrix;
import de.sfuhrm.sudoku.GameSchemas;
import de.sfuhrm.sudoku.Riddle;
import de.sfuhrm.sudoku.Solver;
import github.me_asri.ansu.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    static private final String TAG = MainActivity.class.toString();

    static private final String BUNDLE_KEY_UI_STATE = "ui_state";

    private ActivityMainBinding mBinding;
    private MenuItem mSolveItem;

    private UiState mUiState = UiState.USER_INPUT;
    private Timer mTimer = null;

    private InputMethodManager inputMethodManager;

    private final ActivityResultLauncher<String[]> mOpenDocLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), result -> {
                if (result == null) {
                    return;
                }

                try (InputStream input = getContentResolver().openInputStream(result)) {
                    GameMatrix imported = SudokuHelper.importFile(GameSchemas.SCHEMA_9X9, input);
                    mBinding.board.loadRiddle(imported);

                    Log.i(TAG, "OpenDocument: " + input.available());

                } catch (IOException e) {
                    Toast.makeText(this, "IO exception occurred", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "mCreateDocLauncher: " + e);
                } catch (InvalidParameterException e) {
                    Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<String> mCreateDocLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), result -> {
                if (result == null) {
                    return;
                }

                try (OutputStream output = getContentResolver().openOutputStream(result)) {
                    SudokuHelper.exportFile(mBinding.board.dump(), output);
                } catch (IOException e) {
                    Toast.makeText(this, "IO exception occurred", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "mCreateDocLauncher: " + e);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (savedInstanceState != null) {
            Log.i(TAG, "onCreate: Loading state");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mUiState = savedInstanceState.getSerializable(BUNDLE_KEY_UI_STATE, UiState.class);
            } else {
                mUiState = (UiState) savedInstanceState.getSerializable(BUNDLE_KEY_UI_STATE);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem loadItem = menu.findItem(R.id.loadItem);
        loadItem.setOnMenuItemClickListener(this::onLoadItemClick);

        mSolveItem = menu.findItem(R.id.solveItem);
        mSolveItem.setOnMenuItemClickListener(this::onSolveItemClick);

        MenuItem genItem = menu.findItem(R.id.generateItem);
        genItem.setOnMenuItemClickListener(this::onGenerateItemClick);

        MenuItem clearAnsItem = menu.findItem(R.id.clearAnsItem);
        clearAnsItem.setOnMenuItemClickListener(this::onClearAnsItemClick);

        MenuItem clearBoardItem = menu.findItem(R.id.clearBoardItem);
        clearBoardItem.setOnMenuItemClickListener(this::onClearBoardItemClick);

        MenuItem exportItem = menu.findItem(R.id.exportItem);
        exportItem.setOnMenuItemClickListener(this::onExportItemClick);

        MenuItem unlockTilesItem = menu.findItem(R.id.unlockTilesItem);
        unlockTilesItem.setOnMenuItemClickListener(this::onUnlockTilesItemClick);

        MenuItem exitItem = menu.findItem(R.id.exitItem);
        exitItem.setOnMenuItemClickListener(this::onExitItemClick);

        // Resume UI state
        Log.i(TAG, "onCreateOptionsMenu: Resuming");
        setUiState(mUiState);
        if (mUiState == UiState.SOLVING) {
            startSolutionTimer();
        }

        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(BUNDLE_KEY_UI_STATE, mUiState);
    }

    @Override
    protected void onStop() {
        super.onStop();

        stopSolutionTimer();
    }

    private boolean onLoadItemClick(MenuItem item) {
        mOpenDocLauncher.launch(new String[]{
                "application/octet-stream"
        });

        return true;
    }

    private boolean onSolveItemClick(MenuItem item) {
        if (mTimer != null) {
            stopSolutionTimer();

            setUiState(UiState.USER_INPUT);
        } else {
            if (startSolutionTimer()) {
                setUiState(UiState.SOLVING);
            }
        }

        return true;
    }

    private boolean onGenerateItemClick(MenuItem item) {
        stopSolutionTimer();
        closeKeyboard();

        GameMatrix matrix = Creator.createFull(GameSchemas.SCHEMA_9X9);
        Riddle riddle = Creator.createRiddle(matrix);
        mBinding.board.loadRiddle(riddle);

        setUiState(UiState.USER_INPUT);

        return true;
    }

    private boolean onClearAnsItemClick(MenuItem item) {
        stopSolutionTimer();

        mBinding.board.clearAnswers();

        setUiState(UiState.USER_INPUT);

        return true;
    }

    private boolean onClearBoardItemClick(MenuItem item) {
        stopSolutionTimer();

        mBinding.board.clearBoard();

        return true;
    }

    private boolean onExportItemClick(MenuItem item) {
        stopSolutionTimer();

        mCreateDocLauncher.launch("Riddle.sdk");

        return true;
    }

    private boolean onUnlockTilesItemClick(MenuItem item) {
        stopSolutionTimer();

        mBinding.board.unlockAllTiles();

        return true;
    }

    private boolean onExitItemClick(MenuItem item) {
        finishAndRemoveTask();

        return true;
    }

    private void stopSolutionTimer() {
        if (mTimer == null) {
            return;
        }

        mTimer.cancel();
        mTimer.purge();
        mTimer = null;
    }

    private boolean startSolutionTimer() {
        GameMatrix matrix = mBinding.board.dump();

        Solver solver = new Solver(matrix);
        List<GameMatrix> solutions = solver.solve();
        if (solutions.size() == 0) {
            Toast.makeText(this, "No solution available", Toast.LENGTH_SHORT).show();

            return false;
        }
        GameMatrix solution = solutions.get(0);

        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new ShowSolutionTask(mBinding.board.dump(), solution), 0, 200);

        return true;
    }

    private void setUiState(UiState state) {
        switch (state) {
            case USER_INPUT:
                mSolveItem.setIcon(R.drawable.ic_check);
                mSolveItem.setEnabled(true);

                mBinding.board.setReadonly(false);

                break;
            case SOLVING:
                mSolveItem.setIcon(R.drawable.ic_stop);

                closeKeyboard();

                mBinding.board.setReadonly(true);

                break;
        }

        mUiState = state;
    }

    private void closeKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    class ShowSolutionTask extends TimerTask {
        private final GameMatrix mGame;
        private final GameMatrix mSolution;
        private final int mWidth;
        private int mIndex = 0;


        public ShowSolutionTask(GameMatrix game, GameMatrix solution) {
            super();

            mGame = game;
            mSolution = solution;

            if (game.getSchema().getWidth() != solution.getSchema().getWidth()) {
                throw new InvalidParameterException("Game and solution widths do not match");
            }

            mWidth = game.getSchema().getWidth();
        }

        @Override
        public void run() {
            int i = mIndex / mWidth;
            int j = mIndex % mWidth;
            while (mIndex != mWidth * mWidth && mGame.get(i, j) != 0) {
                mIndex++;

                i = mIndex / mWidth;
                j = mIndex % mWidth;
            }
            if (mIndex == mWidth * mWidth) {
                runOnUiThread(() -> setUiState(UiState.USER_INPUT));

                stopSolutionTimer();
                return;
            }

            byte value = mSolution.get(i, j);
            int finalI = i;
            int finalJ = j;
            runOnUiThread(() -> mBinding.board.place(finalI, finalJ, value, true));

            mIndex++;
        }
    }

    enum UiState {
        USER_INPUT,
        SOLVING,
    }
}