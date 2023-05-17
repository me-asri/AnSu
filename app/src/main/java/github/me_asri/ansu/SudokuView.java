package github.me_asri.ansu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.security.InvalidParameterException;

import de.sfuhrm.sudoku.GameMatrix;
import de.sfuhrm.sudoku.GameMatrixFactory;
import de.sfuhrm.sudoku.GameSchemas;

public class SudokuView extends TableLayout {
    static public final int MATRIX_WIDTH = GameSchemas.SCHEMA_9X9.getWidth();
    static public final int BLOCK_WIDTH = GameSchemas.SCHEMA_9X9.getBlockWidth();

    static private final String BUNDLE_KEY_GAME = "game_array";
    static private final String BUNDLE_KEY_ANS = "answer_array";

    private final GameMatrix mGameMatrix = new GameMatrixFactory().newGameMatrix(GameSchemas.SCHEMA_9X9);
    private final GameMatrix mAnswerMatrix = new GameMatrixFactory().newGameMatrix(GameSchemas.SCHEMA_9X9);

    private final EditText[][] mTiles = new EditText[MATRIX_WIDTH][MATRIX_WIDTH];

    private boolean mIgnoreCheck = true;

    public SudokuView(Context context) {
        super(context);
    }

    public SudokuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        inflate(context, R.layout.sudoku_board, this);
        createBoard(context);
    }

    private void createBoard(Context context) {
        for (int i = 0; i < BLOCK_WIDTH; i++) {
            TableRow row = new TableRow(context);

            for (int j = 0; j < BLOCK_WIDTH; j++) {
                EditText[][] tilesCreated = new EditText[BLOCK_WIDTH][BLOCK_WIDTH];
                TableLayout block = createBlock(context, tilesCreated);
                block.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.shape_border_thick, null));

                for (int x = 0; x < BLOCK_WIDTH; x++) {
                    System.arraycopy(tilesCreated[x], 0, mTiles[i * BLOCK_WIDTH + x], j * BLOCK_WIDTH, BLOCK_WIDTH);
                }

                row.addView(block);
            }

            addView(row);
        }

        invalidate();
    }

    private TableLayout createBlock(Context context, EditText[][] tilesCreated) {
        TableLayout table = new TableLayout(context);
        for (int i = 0; i < BLOCK_WIDTH; i++) {
            TableRow row = new TableRow(context);

            for (int j = 0; j < BLOCK_WIDTH; j++) {
                EditText tile = new EditText(context);

                tile.setWidth(dp(40));
                tile.setHeight(dp(40));
                tile.setTextAlignment(TEXT_ALIGNMENT_CENTER);

                // Prevent user from interacting with the tiles
                tile.setInputType(InputType.TYPE_NULL);
                tile.setFocusable(false);
                tile.setFocusableInTouchMode(false);

                tile.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.shape_border, null));

                tile.setFilters(new InputFilter[]{
                        new SudokuInputFilter()
                });
                tile.addTextChangedListener(new SudokuTextWatcher(tile, i, j));

                if (tilesCreated != null) {
                    tilesCreated[i][j] = tile;
                }

                row.addView(tile);
            }

            table.addView(row);
        }

        return table;
    }

    @SuppressLint("SetTextI18n")
    public void place(int i, int j, byte value, boolean ignoreCheck) {
        if (ignoreCheck) {
            mIgnoreCheck = true;
        }

        mTiles[i][j].setText(Integer.toString(value));
        mAnswerMatrix.set(i, j, value);

        if (ignoreCheck) {
            mIgnoreCheck = false;
        }
    }

    public void loadRiddle(GameMatrix input) {
        if (input.getSchema() != GameSchemas.SCHEMA_9X9) {
            throw new InvalidParameterException("Unsupported GameMatrix schema");
        }

        byte[][] array = input.getArray();
        mGameMatrix.setAll(array);
        mAnswerMatrix.setAll(array);

        reloadTiles();
    }

    public GameMatrix dump() {
        GameMatrix copy = new GameMatrixFactory().newGameMatrix(GameSchemas.SCHEMA_9X9);
        copy.setAll(mAnswerMatrix.getArray());

        return copy;
    }

    private void setTileReadonly(EditText editText, boolean state) {
        editText.setFocusable(!state);
        editText.setFocusableInTouchMode(!state);
        editText.setInputType((state) ? InputType.TYPE_NULL : InputType.TYPE_CLASS_NUMBER);
    }

    private void setTileLock(EditText editText, boolean state) {
        if (state) {
            setTileReadonly(editText, true);
            editText.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.shape_filled_border, null));
        } else {
            setTileReadonly(editText, false);
            editText.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.shape_border, null));
        }
    }

    public void setReadonly(boolean state) {
        for (int i = 0; i < MATRIX_WIDTH; i++) {
            for (int j = 0; j < MATRIX_WIDTH; j++) {
                if (mGameMatrix.get(i, j) == 0) {
                    setTileReadonly(mTiles[i][j], state);
                }
            }
        }
    }

    public void clearAnswers() {
        mAnswerMatrix.setAll(mGameMatrix.getArray());

        reloadTiles();
    }

    public void clearBoard() {
        mGameMatrix.clear();
        mAnswerMatrix.clear();

        reloadTiles();
    }

    public void unlockAllTiles() {
        mGameMatrix.clear();
        mAnswerMatrix.setAll(mAnswerMatrix.getArray());

        for (int i = 0; i < MATRIX_WIDTH; i++) {
            for (int j = 0; j < MATRIX_WIDTH; j++) {
                setTileLock(mTiles[i][j], false);
            }
        }
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        super.onSaveInstanceState();

        Bundle bundle = new Bundle();
        bundle.putSerializable(BUNDLE_KEY_GAME, mGameMatrix.getArray());
        bundle.putSerializable(BUNDLE_KEY_ANS, mAnswerMatrix.getArray());

        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(null);

        Bundle bundle = (Bundle) state;

        byte[][] gameArray;
        byte[][] ansArray;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gameArray = bundle.getSerializable(BUNDLE_KEY_GAME, byte[][].class);
            ansArray = bundle.getSerializable(BUNDLE_KEY_ANS, byte[][].class);
        } else {
            gameArray = (byte[][]) bundle.getSerializable(BUNDLE_KEY_GAME);
            ansArray = (byte[][]) bundle.getSerializable(BUNDLE_KEY_ANS);
        }


        mGameMatrix.setAll(gameArray);
        mAnswerMatrix.setAll(ansArray);

        mIgnoreCheck = false;

        reloadTiles();
    }

    @SuppressLint("SetTextI18n")
    private void reloadTiles() {
        mIgnoreCheck = true;

        byte[][] ansArray = mAnswerMatrix.getArray();
        byte[][] gameArray = mGameMatrix.getArray();
        for (int i = 0; i < MATRIX_WIDTH; i++) {
            for (int j = 0; j < MATRIX_WIDTH; j++) {
                if (ansArray[i][j] == 0) {
                    mTiles[i][j].setText(null);
                    setTileLock(mTiles[i][j], false);
                } else {
                    mTiles[i][j].setText(Byte.toString(ansArray[i][j]));
                    setTileLock(mTiles[i][j], gameArray[i][j] != 0);
                }
            }
        }

        mIgnoreCheck = false;
    }

    @SuppressWarnings("SameParameterValue")
    private int dp(int pixels) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int) (pixels * density + 0.5f);
    }

    private class SudokuTextWatcher implements TextWatcher {
        private final EditText mEditText;
        private final ColorStateList mOldColors;

        private final int mRow;
        private final int mCol;

        public SudokuTextWatcher(EditText editText, int row, int column) {
            super();

            mEditText = editText;
            mOldColors = mEditText.getTextColors();

            mRow = row;
            mCol = column;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (mIgnoreCheck) {
                mEditText.setTextColor(mOldColors);
                return;
            }

            if (s.toString().isEmpty()) {
                mEditText.setTextColor(mOldColors);
                mAnswerMatrix.set(mRow, mCol, (byte) 0);

                return;
            }

            byte value = Byte.parseByte(s.toString());
            if (mAnswerMatrix.canSet(mRow, mCol, value)) {
                mEditText.setTextColor(mOldColors);
                mAnswerMatrix.set(mRow, mCol, value);
            } else {
                mEditText.setTextColor(Color.RED);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    private static class SudokuInputFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                String input = dest.subSequence(0, dstart)
                        + source.subSequence(start, end).toString()
                        + dest.subSequence(dend, dest.length());

                byte value = Byte.parseByte(input);
                if (value <= MATRIX_WIDTH && value >= 1) {
                    return null;
                }
            } catch (NumberFormatException ignored) {
            }

            return "";
        }
    }
}
