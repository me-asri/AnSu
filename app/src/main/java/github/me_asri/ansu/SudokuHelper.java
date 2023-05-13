package github.me_asri.ansu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.InvalidParameterException;

import de.sfuhrm.sudoku.GameMatrix;
import de.sfuhrm.sudoku.GameMatrixFactory;
import de.sfuhrm.sudoku.GameSchema;

public class SudokuHelper {
    static public GameMatrix importFile(GameSchema gameSchema, InputStream input) throws IOException {
        GameMatrix gameMatrix = new GameMatrixFactory().newGameMatrix(gameSchema);

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        int i = 0;
        while (reader.ready()) {
            String line = reader.readLine();
            if (line.charAt(0) == '#') {
                continue; // Ignore comments
            }

            int lineLen = line.length();
            if (lineLen > gameSchema.getWidth()) {
                throw new InvalidParameterException("Invalid file");
            }
            for (int j = 0; j < lineLen; j++) {
                char c = line.charAt(j);

                if (c == '.') {
                    gameMatrix.set(i, j, (byte) 0);
                } else {
                    byte value;
                    try {
                        value = Byte.parseByte(String.valueOf(c));
                    } catch (NumberFormatException nfe) {
                        throw new InvalidParameterException("Invalid file");
                    }
                    gameMatrix.set(i, j, value);
                }
            }

            i++;
        }

        return gameMatrix;
    }

    static public void exportFile(GameMatrix gameMatrix, OutputStream output) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));
        int width = gameMatrix.getSchema().getWidth();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                byte value = gameMatrix.get(i, j);
                if (value == 0) {
                    writer.write(".");
                } else {
                    writer.write(Byte.toString(value));
                }
            }
            writer.write('\n');
        }
        writer.flush();
    }
}
