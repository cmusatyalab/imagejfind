import ij.WindowManager;
import ij.plugin.*;
import ij.plugin.filter.Analyzer;
import ij.measure.ResultsTable;
import ij.plugin.frame.Recorder;
import ij.*;
import ijloader.IJLoader;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.TextArea;
import java.util.concurrent.*;

public class Diamond_Filter implements PlugIn {

    public void run(String arg0) {
        System.err.println("Diamond filter started...");
        rTable = Analyzer.getResultsTable();

        if (IJ.macroRunning()) {
            String result = evaluate(Macro.getValue(Macro.getOptions(),
                    MACRO_FIELD_NAME, ""));

            System.err.println(" Writing result: " + result);
            if (result.equals("") || !isValidResult(result)) {
                System.err.print(" Bad result: ");
                System.err.println(result);
                result = "0.0";
            }
            
            IJLoader.writeResult(result);
        } else {
            System.err.println("Opening dialog...");
            new DiamondFilterDialog();
        }

    }

    private class DiamondFilterDialog extends Dialog implements ActionListener,
            TextListener, Runnable {
        public DiamondFilterDialog() {
            super(WindowManager.getFrame("Results"), "Diamond Filter setup",
                    true);

            scheduler = new ScheduledThreadPoolExecutor(1);

            Font font = new Font("Monospaced", Font.PLAIN, 12);

            exprArea = new TextArea(5, 40);
            exprArea.addTextListener(this);
            exprArea.setFont(font);

            feedbackArea = new TextArea(5, 40);
            feedbackArea.setEditable(false);
            feedbackArea.setFont(font);

            okButton = new Button("OK");
            okButton.setEnabled(false);
            okButton.addActionListener(this);

            Button cancelButton = new Button("Cancel");
            cancelButton.addActionListener(this);

            Panel controlPanel = new Panel(new GridLayout(1, 2));
            controlPanel.add(okButton);
            controlPanel.add(cancelButton);

            this.setLayout(new GridLayout(4, 1));
            this.add(new ExpressionHelperPanel(exprArea, rTable));
            this.add(exprArea);
            this.add(feedbackArea);
            this.add(controlPanel);

            this.setSize(320, 240);

            feedbackTask = scheduleFeedbackTask();

            this.setVisible(true);
        }

        public String getResult() {
            return result;
        }

        /* Clicking on OK or cancel */
        public void actionPerformed(ActionEvent e) {
            this.dispose();

            if (e.getActionCommand().equals("OK")) {
                Recorder.recordOption(MACRO_FIELD_NAME, exprArea.getText());
                result = evaluate(exprArea.getText());
                cancelled = false;
            } else {
                result = "";
                cancelled = true;
            }
        }

        /* Modifying the text area */
        public void textValueChanged(TextEvent event) {
            if (!feedbackTask.cancel(false)) {
                try {
                    feedbackTask.get();
                } catch (ExecutionException e) {
                    /* Nothing */
                } catch (InterruptedException e) {
                    /* Nothing */
                }
            }

            okButton.setEnabled(false);
            feedbackArea.setText("");
            feedbackTask = scheduleFeedbackTask();
        }

        public ScheduledFuture<Object> scheduleFeedbackTask() {
            return scheduler.schedule(Executors.callable(this), EVAL_DELAY_MS,
                    TimeUnit.MILLISECONDS);
        }

        /* updating feedback */
        public void run() {
            if (isValidResult(exprArea.getText())) {
                feedbackArea.setText(evaluate(exprArea.getText()));
                okButton.setEnabled(true);
            } else {
                /*
                 * on error, we schedule yet another deferred task it will show
                 * the error after another period of time
                 */
                feedbackTask = scheduler.schedule(Executors
                        .callable(new Runnable() {
                            public void run() {
                                feedbackArea.setText(evaluate(exprArea
                                        .getText()));
                            }
                        }), ERROR_DELAY_MS, TimeUnit.MILLISECONDS);
            }

        }

        private Button okButton;

        private TextArea exprArea;

        private TextArea feedbackArea;

        private ScheduledExecutorService scheduler;

        private ScheduledFuture<Object> feedbackTask;

        private boolean cancelled;

        private String result;

        private static final int EVAL_DELAY_MS = 250;

        private static final int ERROR_DELAY_MS = 2750;

    }

    private static class ExpressionHelperPanel extends Panel implements
            ActionListener {

        public ExpressionHelperPanel(TextComponent tc, ResultsTable rTable) {
            this.tc = tc;

            this.setLayout(new GridLayout(1, 3));

            functionChooser = new Choice();
            for (AggregatorType value : AggregatorType.values()) {
                functionChooser.add(value.toString());
            }

            columnChooser = new Choice();
            for (int i = 0; i < ResultsTable.MAX_COLUMNS; i++) {
                String colName = rTable.getColumnHeading(i);
                if ((colName != null) && (rTable.getColumn(i) != null)) {
                    columnChooser.add(colName);
                }
            }

            Button actionButton = new Button("Insert");
            actionButton.addActionListener(this);

            add(functionChooser);
            add(columnChooser);
            add(actionButton);
        }

        public void actionPerformed(ActionEvent event) {
            String fieldText = tc.getText();
            tc.setText(fieldText + getSubExpr());
        }

        private String getSubExpr() {
            return functionChooser.getSelectedItem()
                    + "("
                    + ColumnIdToken
                            .makeColExpr(columnChooser.getSelectedItem()) + ")";
        }

        private final TextComponent tc;

        private final Choice functionChooser;

        private final Choice columnChooser;

    }

    protected String evaluate(String expr) {
        String ret;
        try {
            Queue<BaseToken> tokens = tokenize(expr);

            if (tokens.size() == 0) {
                return "";
            }

            ReferenceString refStr = new ReferenceString(tokens);

            try {
                double result = getExpr(tokens);
                if (!tokens.isEmpty()) {
                    throw new ParseException(
                            "There is extra code at the end of the line!");
                }
                ret = Double.toString(result);
            } catch (ParseException e) {
                StringBuilder retBuilder = new StringBuilder();
                retBuilder.append("Error!\n");
                retBuilder.append(refStr.toString() + "\n");
                for (int i = 0; i < refStr.location(tokens.size()); i++)
                    retBuilder.append(' ');
                retBuilder.append("^\n");
                retBuilder.append(e.getMessage());
                ret = retBuilder.toString();
            }
        } catch (TokenizeException e) {
            ret = "Error!\n" + e.getMessage();
        }
        return ret;
    }

    protected boolean isValidResult(String expr) {
        String evalResult = evaluate(expr);

        return (!evalResult.startsWith("Error!\n") && !evalResult.equals(""));
    }

    protected double getExpr(Queue<BaseToken> q) throws ParseException {
        double term = getTerm(q);

        BaseToken nextToken = q.peek();

        if (nextToken == null) {
            return term;
        }

        double expr;
        switch (nextToken.getType()) {
        case OpPlus:
            q.remove();
            expr = getExpr(q);
            return term + expr;
        case OpMinus:
            q.remove();
            expr = getExpr(q);
            return term - expr;
        default:
            return term;
        }
    }

    protected double getTerm(Queue<BaseToken> q) throws ParseException {
        double factor = getFactor(q);

        BaseToken nextToken = q.peek();

        if (nextToken == null) {
            return factor;
        }

        double term;
        switch (nextToken.getType()) {
        case OpMult:
            q.remove();
            term = getTerm(q);
            return factor * term;
        case OpDiv:
            q.remove();
            term = getTerm(q);
            return factor / term;
        default:
            return factor;
        }
    }

    protected double getFactor(Queue<BaseToken> q) throws ParseException {
        BaseToken nextToken = q.peek();

        if (nextToken == null) {
            throw new ParseException(
                    "Expecting more, but ran into the end of the line!");
        }

        switch (nextToken.getType()) {
        case OpMinus:
            q.remove();
            double factor = getFactor(q);
            return -factor;
        case Literal:
            try {
                LiteralToken literal = (LiteralToken) nextToken;
                q.remove();
                return literal.getValue();
            } catch (ClassCastException e) {
                throw new ParseException(
                        "Found a literal but did not have LiteralToken, contact developer.");
            }
        case Aggregator:
            try {
                AggregatorToken agg = (AggregatorToken) nextToken;
                AggregatorType aggType = agg.getAggType();

                q.remove();
                float[] values = getColumn(q);

                return aggType.run(values);
            } catch (ClassCastException e) {
                throw new ParseException(
                        "Found an aggregator but did not have AggregatorToken, contact developer.");
            }
        case LParen:
            q.remove();
            double expr = getExpr(q);
            getRParen(q);
            return expr;
        default:
            throw new ParseException("You seem to be missing something here.");
        }
    }

    protected float[] getColumn(Queue<BaseToken> q) throws ParseException {
        if (rTable == null) {
            throw new ParseException("Results table necessary, but no "
                    + "results table found!");
        }

        BaseToken nextToken = q.peek();

        float[] ret;
        switch (nextToken.getType()) {
        case LParen:
            q.remove();
            ret = getColumn(q);
            getRParen(q);
            break;
        case ColumnID:
            try {
                ColumnIdToken colIdToken = (ColumnIdToken) nextToken;
                int index = rTable.getColumnIndex(colIdToken.getName());
                if (index == ResultsTable.COLUMN_NOT_FOUND) {
                    throw new ParseException("Invalid column name.");
                }
                ret = rTable.getColumn(index);
                q.remove();
                break;
            } catch (ClassCastException e) {
                throw new ParseException(
                        "Found a column ID, but did not have ColumnIDToken, contact developer.");
            }
        default:
            throw new ParseException(
                    "Expecting a table column ID, but found something else!");
        }
        return ret;
    }

    protected void getRParen(Queue<BaseToken> q) throws ParseException {
        BaseToken nextToken = q.poll();

        if ((nextToken == null) || (nextToken.getType() != TokenType.RParen)) {
            throw new ParseException(
                    "Mismatched parentheses! Expected right parenthesis.");
        }
    }

    protected static Queue<BaseToken> tokenize(String expr)
            throws TokenizeException {
        StreamTokenizer st = new StreamTokenizer(new StringReader(expr));
        st.eolIsSignificant(false);
        st.ordinaryChar('-');
        st.ordinaryChar('/');
        st.ordinaryChar('.');
        st.wordChars('_', '_');
        st.quoteChar(QUOTE_CHAR);
        st.whitespaceChars(128, 255);
        st.slashStarComments(true);
        st.slashSlashComments(true);

        Queue<BaseToken> ret = new LinkedList<BaseToken>();

        getNextToken(st);
        while (st.ttype != StreamTokenizer.TT_EOF) {
            BaseToken nextToken;

            switch (st.ttype) {
            case StreamTokenizer.TT_NUMBER:
                nextToken = new LiteralToken(st.nval);
                break;
            case StreamTokenizer.TT_WORD:
                nextToken = getNewAggToken(st.sval);
                break;
            case StreamTokenizer.TT_EOF:
                throw new RuntimeException(
                        "Checking for EOF in tokenizer done incorrectly, contact developer.");
            case StreamTokenizer.TT_EOL:
                throw new RuntimeException(
                        "Checking for EOL in tokenizer done incorrectly, contact developer.");
            case QUOTE_CHAR:
                nextToken = new ColumnIdToken(st.sval);
                break;
            default:
                TokenType ttype = TokenType.getTokenType((char) st.ttype);
                if (ttype == null) {
                    throw new TokenizeException("Invalid character: "
                            + (char) st.ttype);
                }
                nextToken = new BaseToken(ttype);
            }

            ret.add(nextToken);
            getNextToken(st);
        }

        return ret;
    }

    protected static BaseToken getNewAggToken(String tokenVal)
            throws TokenizeException {
        AggregatorType aggType;
        try {
            aggType = AggregatorType.valueOf(tokenVal.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TokenizeException("Unknown command: " + tokenVal);
        }

        return new AggregatorToken(aggType);
    }

    protected static void getNextToken(StreamTokenizer st) {
        try {
            st.nextToken();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Received IO Exception from StringReader, contact developer.");
        }
    }

    protected static enum TokenType {
        Literal, OpPlus('+'), OpMinus('-'), OpMult('*'), OpDiv('/'), LParen('('), RParen(
                ')'), Aggregator, ColumnID;

        TokenType() {
            hasChar = false;
            myChar = ' ';
        }

        TokenType(char c) {
            hasChar = true;
            myChar = c;
        }

        public static TokenType getTokenType(char c) {
            return charToToken.get(c);
        }

        public char getAssocChar() {
            if (hasChar)
                return myChar;
            else
                return 0;
        }

        private final boolean hasChar;

        private final char myChar;

        private static final Map<Character, TokenType> charToToken = new HashMap<Character, TokenType>();

        static {
            for (TokenType ttype : TokenType.values()) {
                if (ttype.hasChar) {
                    charToToken.put(ttype.myChar, ttype);
                }
            }
        }
    }

    protected static class BaseToken {
        public BaseToken(TokenType type) {
            this.type = type;
        }

        public TokenType getType() {
            return type;
        }

        public String toString() {
            char c = type.getAssocChar();

            if (c != 0) {
                return Character.toString(c);
            } else {
                return "";
            }
        }

        private final TokenType type;
    }

    protected static class LiteralToken extends BaseToken {
        public LiteralToken(double value) {
            super(TokenType.Literal);
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public String toString() {
            return Double.toString(value);
        }

        private final double value;
    }

    protected static class AggregatorToken extends BaseToken {
        public AggregatorToken(AggregatorType aggType) {
            super(TokenType.Aggregator);
            this.aggType = aggType;
        }

        public AggregatorType getAggType() {
            return aggType;
        }

        public String toString() {
            return aggType.toString().toLowerCase();
        }

        private final AggregatorType aggType;
    }

    protected static class ColumnIdToken extends BaseToken {
        public ColumnIdToken(String colName) {
            super(TokenType.ColumnID);
            this.colName = colName;
        }

        public String getName() {
            return colName;
        }

        public static String makeColExpr(String s) {
            return QUOTE_CHAR + s + QUOTE_CHAR;
        }

        public String toString() {
            return makeColExpr(colName);
        }

        private final String colName;
    }

    protected static enum AggregatorType {
        /* these identifiers must be uppercase */
        SUM(new SumEngine()), COUNT(new CountEngine()), AVERAGE(new AvgEngine()), PRODUCT(
                new ProdEngine()), MIN(new MinEngine()), MAX(new MaxEngine());

        AggregatorType(AggregatorEngine eng) {
            this.eng = eng;
        }

        public double run(float[] values) {
            return eng.run(values);
        }

        private final AggregatorEngine eng;

    }

    protected static abstract class AggregatorEngine {
        public abstract double run(float[] values);
    }

    protected static class SumEngine extends AggregatorEngine {
        public double run(float[] values) {
            double ret = 0.0;
            for (double value : values) {
                ret += value;
            }
            return ret;
        }
    }

    protected static class CountEngine extends AggregatorEngine {
        public double run(float[] values) {
            return values.length;
        }
    }

    protected static class AvgEngine extends AggregatorEngine {
        public double run(float[] values) {
            double sum = 0.0;
            for (double value : values) {
                sum += value;
            }
            return sum / ((double) values.length);
        }
    }

    protected static class ProdEngine extends AggregatorEngine {
        public double run(float[] values) {
            double ret = 1.0;
            for (double value : values) {
                ret *= value;
            }
            return ret;
        }
    }

    protected static class MinEngine extends AggregatorEngine {
        public double run(float[] values) {
            if (values.length == 0)
                return 0.0;
            double ret = Double.MAX_VALUE;
            for (double value : values) {
                if (value < ret)
                    ret = value;
            }
            return ret;
        }
    }

    protected static class MaxEngine extends AggregatorEngine {
        public double run(float[] values) {
            if (values.length == 0)
                return 0.0;
            double ret = Double.MIN_VALUE;
            for (double value : values) {
                if (value > ret)
                    ret = value;
            }
            return ret;
        }
    }

    protected static class TokenizeException extends Exception {
        public TokenizeException(String msg) {
            super(msg);
        }
    }

    protected static class ParseException extends Exception {
        public ParseException(String msg) {
            super(msg);
        }
    }

    private static class ReferenceString {
        public ReferenceString(Collection<BaseToken> tokens) {
            StringBuilder builder = new StringBuilder();
            locs = new int[tokens.size() + 1];

            int i = tokens.size();
            for (BaseToken token : tokens) {
                locs[i] = builder.length();
                i--;
                builder.append(token.toString());
                builder.append(' ');
            }

            locs[0] = builder.length();
            str = builder.toString();
        }

        public String toString() {
            return str;
        }

        public int location(int tokensLeft) {
            return locs[tokensLeft];
        }

        private final String str;

        private final int locs[];

    }

    private static final String EXPRESSION_FIELD = "Expression";

    private static final char QUOTE_CHAR = '\'';

    private ResultsTable rTable;

    private static final String MACRO_FIELD_NAME = "expr";

}
