/**
 * @author João Augusto Costa Branco Marado Torres
 * @version 0.6, 2024/04/21
 */
package pt.ipbeja.app.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.ipbeja.app.model.throwables.InvalidInGameChangeException;
import pt.ipbeja.app.model.words_provider.DBWordsProvider;
import pt.ipbeja.app.model.words_provider.WordsProvider;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Game model
 */
public class WSModel {
    /**
     * A natural number representing the minimum acceptable length for a matrix side.
     */
    public static final int MIN_SIDE_LEN = 5;
    /**
     * A natural number representing the maximum acceptable length for a matrix side.
     */
    public static final int MAX_SIDE_LEN = 12;
    private static final String INVALID_SIDE_LEN_MSG_FORMAT = String.format("the %s provided is invalid! it needs to be a number between %d and %d", "%s", MIN_SIDE_LEN, MAX_SIDE_LEN);

    private final @NotNull Random random;

    /**
     * The number of lines in the matrix.
     */
    private int lines;
    /**
     * The number of columns in the matrix.
     */
    private int cols;
    /**
     * The matrix of {@link Cell}s.
     * <p>Can be a simple <code>Cell[lines*cols]</code> and to access a <code>Cell</code> in line <code>a</code> and
     * column <code>b</code> you just <code>this.matrix[a * this.cols + b]</code>.</p>
     *
     * @see Cell
     */
    private List<List<Cell>> matrix;

    private WSView view;

    /**
     * Set of valid words that came from the last database provided using {@link #setWordsProvider(WordsProvider)}.
     *
     * @see #setWordsProvider(WordsProvider)
     */
    private Set<String> words;
    /**
     * Subset of {@link #words} with words that can fit in the matrix.
     */
    private Set<String> words_in_use;
    /**
     * Subset of {@link #words} with words that are in game.
     */
    private Set<String> words_in_game;
    /**
     * Subset of {@link #words_in_use} of the works that are currently on the matrix to be found.
     *
     * @see #words_found
     */
    private Set<String> words_to_find;
    /**
     * Subset of {@link #words_in_use} of the works that are currently on the matrix, that were already found.
     *
     * @see #words_to_find
     */
    private Set<String> words_found;

    /**
     * Represents if a game it's currently happening.
     */
    private boolean in_game;

    private @Nullable Position start_selected;

    private ResultsSaver saver;

    private int maxWords;

    public WSModel() {
        this.random = new Random();
        this.maxWords = 0;
    }

    public WSModel(int lines, int cols) {
        this();
        try {
            this.setDimensions(lines, cols);
        } catch (InvalidInGameChangeException e) {
            throw new RuntimeException(e);
        }
    }

    public WSModel(int lines, int cols, @NotNull WordsProvider provider) {
        this(lines, cols);
        this.setWordsProvider(provider);
    }

    /**
     * Creates the model for a words matrix game.
     *
     * @param lines initial number of lines for the game to have.
     * @param cols  initial number of columns for the game to have.
     * @param file  the database with the words to put in the game.
     */
    public WSModel(int lines, int cols, @NotNull String file) {
        this(lines, cols, new DBWordsProvider(Paths.get(file).toFile()));
    }

    /**
     * Creates the model for a words matrix game.
     *
     * @param lines initial number of lines for the game to have.
     * @param cols  initial number of columns for the game to have.
     * @param file  the database with the words to put in the game.
     */
    public WSModel(int lines, int cols, @NotNull URI file) {
        this(lines, cols, new DBWordsProvider(Paths.get(file).toFile()));
    }

    /**
     * Creates the model for a words matrix game.
     *
     * @param lines initial number of lines for the game to have.
     * @param cols  initial number of columns for the game to have.
     * @param file  the database with the words to put in the game.
     */
    public WSModel(int lines, int cols, @NotNull Path file) {
        this(lines, cols, new DBWordsProvider(file.toFile()));
    }

    /**
     * Sets the dimensions of the matrix (both lines and columns). To set lines and/or columns separately use
     * {@link #setLines(int)} and {@link #setCols(int)}.
     *
     * @param lines The number of lines for the matrix to have
     * @param cols The number of columns for the matrix to have
     *
     * @throws InvalidInGameChangeException In case of trying to change the dimensions mid-game.
     * @throws IllegalArgumentException If dimensions provided aren't allowed.
     *
     * @see #setLines(int)
     * @see #setCols(int)
     */
    public void setDimensions(int lines, int cols) throws InvalidInGameChangeException {
        if (this.in_game) {
            throwInvalidInGameChange();
        }

        if (lines < 0 || cols < 0) {
            throw new IllegalArgumentException("`lines` and `cols` are natural numbers");
        }

        boolean valid_lines = lines >= MIN_SIDE_LEN && lines <= MAX_SIDE_LEN;
        boolean valid_cols = cols >= MIN_SIDE_LEN && cols <= MAX_SIDE_LEN;
        if (!valid_lines || !valid_cols) {
            boolean both_invalid = !valid_lines && !valid_cols;
            String invalid = (valid_lines ? "" : "`lines``") + (both_invalid ? " and " : "") + (valid_cols ? "" : "`cols``");
            String msg = String.format(INVALID_SIDE_LEN_MSG_FORMAT, invalid);
            throw new IllegalArgumentException(msg);
        }

        this.lines = lines;
        this.cols = cols;

        if (this.words != null && !this.words.isEmpty()) {
            this.calcUsableWords();
        }
    }

    /**
     * @return The number of lines in the matrix
     *
     * @see #setLines(int)
     * @see #setDimensions(int, int)
     */
    public int getLines() {
        return this.lines;
    }

    /**
     * Sets the lines of the matrix. To set columns use {@link #setCols(int)} and to set both at the same time
     * {@link #setDimensions(int, int)}.
     *
     * @param lines The number of lines for the matrix to have
     *
     * @throws InvalidInGameChangeException In case of trying to change the dimensions mid-game.
     * @throws IllegalArgumentException If dimensions provided aren't allowed.
     *
     * @see #setCols(int)
     * @see #setDimensions(int, int)
     */
    public void setLines(int lines) throws InvalidInGameChangeException {
        if (this.in_game) {
            throwInvalidInGameChange();
        }

        if (lines < 0) {
            throw new IllegalArgumentException("`lines` are natural numbers");
        }

        boolean valid_lines = lines >= MIN_SIDE_LEN && lines <= MAX_SIDE_LEN;
        if (!valid_lines) {
            String msg = String.format(INVALID_SIDE_LEN_MSG_FORMAT, "`lines`");
            throw new IllegalArgumentException(msg);
        }

        this.lines = lines;

        if (this.words != null && !this.words.isEmpty()) {
            this.calcUsableWords();
        }
    }

    /**
     * @return The number of columns in the matrix
     *
     * @see #setCols(int)
     * @see #setDimensions(int, int)
     */
    public int getCols() {
        return this.cols;
    }

    /**
     * Sets the columns of the matrix. To set lines use {@link #setLines(int)} and to set both at the same time
     * {@link #setDimensions(int, int)}.
     *
     * @param cols The number of columns for the matrix to have
     *
     * @throws InvalidInGameChangeException In case of trying to change the dimensions mid-game.
     * @throws IllegalArgumentException If dimensions provided aren't allowed.
     *
     * @see #setLines(int)
     * @see #setDimensions(int, int)
     */
    public void setCols(int cols) throws InvalidInGameChangeException {
        if (this.in_game) {
            throwInvalidInGameChange();
        }

        if (cols < 0) {
            throw new IllegalArgumentException("`cols` are natural numbers");
        }

        boolean valid_cols = cols >= MIN_SIDE_LEN && cols <= MAX_SIDE_LEN;
        if (!valid_cols) {
            String msg = String.format(INVALID_SIDE_LEN_MSG_FORMAT, "`cols`");
            throw new IllegalArgumentException(msg);
        }

        this.cols = cols;

        if (this.words != null && !this.words.isEmpty()) {
            this.calcUsableWords();
        }
    }

    private void calcUsableWords() {
        this.words_in_use = new TreeSet<>();
        for (String w : words) {
            if (this.wordFitsGrid(w)) {
                this.words_in_use.add(w);
            }
        }
    }

    private boolean wordFitsHorizontally(@NotNull String word) {
        return word.length() <= this.cols;
    }

    private boolean wordFitsVertically(@NotNull String word) {
        return word.length() <= this.lines;
    }

    private boolean wordFitsGrid(@NotNull String word) {
        return this.wordFitsHorizontally(word) || this.wordFitsVertically(word);
    }

    private void initClearGrid() {
        this.matrix = new ArrayList<>(this.lines);
        for (int i = 0; i < this.lines; i++) {
            List<Cell> line = new ArrayList<>(this.cols);
            for (int j = 0; j < this.cols; j++) {
                line.add(null);
            }
            this.matrix.add(line);
        }
    }

    private void populateGrid() {
        this.words_to_find = new TreeSet<>();

        List<String> words = new ArrayList<>(this.words_in_use);
        Collections.shuffle(words);

        int max = this.words_in_use.size();
        if (this.maxWords != 0 && this.maxWords < max) {
            max = this.maxWords;
        }

        this.words_in_game = new HashSet<>(words.subList(0, max));

        for (String w : this.words_in_game) {
            this.words_to_find.add(w);
            /* orientation: true for horizontal and false for vertical */
            boolean orientation = this.random.nextBoolean();

            // test if orientation is possible, if not, change it
            if (orientation) { // horizontal
                orientation = this.wordFitsHorizontally(w);
            } else { // vertical
                orientation = !this.wordFitsVertically(w);
            }

            try {
                if (orientation) { // horizontal
                    this.addWordHorizontally(w);
                } else { // vertical
                    this.addWordVertically(w);
                }
            } catch (Exception ignored) {
                try {
                    if (orientation && this.wordFitsVertically(w)) {
                        this.addWordVertically(w);
                    } else if (!orientation && this.wordFitsHorizontally(w)) {
                        this.addWordHorizontally(w);
                    } else {
                        this.words_to_find.remove(w);
                    }
                } catch (Exception e) {
                    this.words_to_find.remove(w);
                }
            }
        }

        if (this.words_to_find.isEmpty()) {
            throw new RuntimeException("No words could be used in the game");
        }
    }

    private void fillGrid() {
        for (int i = 0; i < this.lines; i++) {
            List<Cell> l = this.matrix.get(i);
            for (int j = 0; j < this.cols; j++) {
                if (l.get(j) == null) {
                    l.set(j, new Cell((char) this.random.nextInt('A', 'Z' + 1)));
                }
            }
            this.matrix.set(i, l);
        }
    }

    private void initGrid() {
        this.initClearGrid();
        this.populateGrid();
        this.fillGrid();
    }

    private void addWordHorizontally(@NotNull String word) {
        char[] chars = word.toCharArray();

        Set<String> invalids = new TreeSet<>();

        boolean direction = this.random.nextBoolean();
        int walk = direction ? 1 : -1;

        while (true) {
            if (invalids.size() >= (this.lines * (this.cols - word.length() + 1))) {
                throw new RuntimeException("no space to add the word");
            }

            int start = this.random.nextInt(0, this.cols - word.length() + 1);
            if (!direction) {
                start += word.length();
            }
            int pos = this.random.nextInt(0, this.lines);

            if (invalids.contains(start + ";" + pos)) {
                continue;
            }

            List<Cell> line = this.matrix.get(pos);
            Set<Integer> unchanged_pos = new TreeSet<>();
            boolean invalid_pos = false;
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (line.get(start) != null && line.get(start).letter() != c) {
                    while (i-- > 0) {
                        start -= walk;
                        if (!unchanged_pos.contains(start)) {
                            line.set(start, null);
                            this.matrix.set(pos, line);
                        }
                    }
                    invalid_pos = true;
                }

                if (invalid_pos) {
                    break;
                }

                if (line.get(start) == null || line.get(start).letter() != c) {
                    line.set(start, new Cell(c));
                } else {
                    unchanged_pos.add(start);
                }

                start += walk;
            }

            if (!invalid_pos) {
                this.matrix.set(pos, line);
                break;
            } else {
                invalids.add(start + ";" + pos);
            }
        }
    }

    private void addWordVertically(@NotNull String word) {
        char[] chars = word.toCharArray();

        Set<String> invalids = new TreeSet<>();

        boolean direction = this.random.nextBoolean();
        int walk = direction ? 1 : -1;

        while (true) {
            if (invalids.size() == (this.cols * (this.lines - word.length() + 1))) {
                throw new RuntimeException("no space to add the word");
            }

            int start = this.random.nextInt(0, this.lines - word.length() + 1);
            if (!direction) {
                start += word.length();
            }
            int pos = this.random.nextInt(0, this.cols);

            if (invalids.contains(start + ";" + pos)) {
                continue;
            }

            Set<Integer> unchanged_pos = new TreeSet<>();
            boolean invalid_pos = false;
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                List<Cell> line = this.matrix.get(start);
                if (line.get(pos) != null && line.get(pos).letter() != c) {
                    while (i-- > 0) {
                        start -= walk;
                        line = this.matrix.get(start);
                        if (!unchanged_pos.contains(start)) {
                            line.set(pos, null);
                            this.matrix.set(start, line);
                        }
                    }
                    invalid_pos = true;
                }

                if (invalid_pos) {
                    break;
                }

                if (line.get(pos) == null || line.get(pos).letter() != c) {
                    line.set(pos, new Cell(c));
                } else {
                    unchanged_pos.add(start);
                }

                this.matrix.set(start, line);

                start += walk;
            }

            if (!invalid_pos) {
                break;
            } else {
                invalids.add(start + ";" + pos);
            }
        }
    }

    public boolean findWord(@NotNull Position pos) {
        if (!this.in_game) {
            throw new RuntimeException();
        }

        if (this.start_selected == null) {
            this.start_selected = pos;
            return true;
        }

        Position start_pos = this.start_selected;
        this.start_selected = null;

        String word = getPossibleWord(start_pos, pos);
        if (this.wordFound(word)) {
            this.view.wordFound(start_pos, pos);
            this.view.update(new MessageToUI(List.of(), word));
            if (this.allWordsWereFound()) {
                this.endGame();
            }
            return true;
        } else {
            return false;
        }
    }

    private @NotNull String getPossibleWord(@NotNull Position start_pos, @NotNull Position end_pos) {
        StringBuilder possible = new StringBuilder();
        if (start_pos.line() == end_pos.line()) {
            List<Cell> line = this.matrix.get(end_pos.line());
            int start = Math.min(start_pos.col(), end_pos.col());
            int end = Math.max(start_pos.col(), end_pos.col());
            for (int i = start; i <= end; i++) {
                possible.append(line.get(i).letter());
            }
        } else if (start_pos.col() == end_pos.col()) {
            int start = Math.min(start_pos.line(), end_pos.line());
            int end = Math.max(start_pos.line(), end_pos.line());
            for (int i = start; i <= end; i++) {
                List<Cell> line = this.matrix.get(i);
                possible.append(line.get(end_pos.col()).letter());
            }

        }
        return possible.toString();
    }

    public @NotNull GameResults endGame() {
        this.in_game = false;
        GameResults res = this.curGameResults();
        this.view.gameEnded(res);
        if (saver != null) {
            this.saver.save(res);
        }
        return res;
    }

    public @NotNull GameResults curGameResults() {
        return new GameResults(this.words_in_game, this.words_found);
    }

    public boolean gameEnded() {
        return this.words_in_game.size() == this.words_found.size();
    }

    public void startGame() {
        if (in_game) {
            throw new RuntimeException();
        }

        this.initGrid();
        this.in_game = true;
        this.words_found = new TreeSet<>();

        this.view.gameStarted();
    }

    public void registerView(WSView wsView) {
        this.view = wsView;
    }

    /**
     * Get the text in a position
     *
     * @param position position
     * @return the text in the position
     */
    public Cell textInPosition(@NotNull Position position) {
        return this.matrix.get(position.line()).get(position.col());
    }


    /**
     * Check if all words were found
     *
     * @return true if all words were found
     */
    public boolean allWordsWereFound() {
        if (!this.in_game) {
            throw new RuntimeException();
        }
        return this.words_to_find.isEmpty();
    }

    /**
     * Check if the word is in the board
     *
     * @param word word
     * @return true if the word is in the board
     */
    public boolean wordFound(String word) {
        if (!this.in_game) {
            throw new RuntimeException();
        }

        word = word.toUpperCase();

        // https://stackoverflow.com/questions/7569335/reverse-a-string-in-java
        String reversed = new StringBuilder(word).reverse().toString();
        if (this.words_to_find.contains(word)) {
            this.words_to_find.remove(word);
            this.words_found.add(word);
            return true;
        } else if (this.words_to_find.contains(reversed)) {
            this.words_to_find.remove(reversed);
            this.words_found.add(reversed);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if the word with wildcard is in the board
     *
     * @param word word
     * @return true if the word with wildcard is in the board
     */
    public boolean wordWithWildcardFound(String word) {
        if (!this.in_game) {
            throw new RuntimeException();
        }

        word = word.toUpperCase();

        if (this.words_to_find.contains(word)) {
            this.words_to_find.remove(word);
            this.words_found.add(word);
            return true;
        } else {
            return false;
        }
    }

    public void setSaver(ResultsSaver saver) {
        this.saver = saver;
    }

    public void setWordsProvider(@NotNull WordsProvider provider) {
        this.words = new TreeSet<>();
        this.words_in_use = new TreeSet<>();

        String line;
        while ((line = provider.getLine()) != null) {
            String[] words = this.parseLine(line);

            if (words == null) {
                continue;
            }

            for (String word : words) {
                this.words.add(word);
                if (this.wordFitsGrid(word)) {
                    this.words_in_use.add(word);
                }
            }
        }
    }

    private @Nullable String[] parseLine(String line) {
        line = line.trim();

        if (line.isBlank()) {
            return null;
        }

        try {
            // https://stackoverflow.com/questions/51732439/regex-accented-characters-for-special-field
            // https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
            // http://www.unicode.org/reports/tr24/

            String[] words = line.split("[^\\p{sc=LATN}]");
            for (int i = 0; i < words.length; i++) {
                words[i] = words[i].toUpperCase();
            }
            return words;
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    public int wordsInUse() {
        if (this.words_in_use != null) {
            return this.words_in_use.size();
        }
        return 0;
    }

    public static final String INVALID_IN_GAME_CHANGE_MSG_ERR = "A game it's currently happening. Cannot perform this action";
    private static void throwInvalidInGameChange() throws InvalidInGameChangeException {
        throw new InvalidInGameChangeException(INVALID_IN_GAME_CHANGE_MSG_ERR);
    }

    public void setMaxWords(int maxWords) {
        assert maxWords > 0;
        this.maxWords = maxWords;
    }
}
