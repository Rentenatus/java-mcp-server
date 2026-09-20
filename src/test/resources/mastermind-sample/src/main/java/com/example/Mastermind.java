package com.example;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Mastermind extends JFrame {
    private static final int CODE_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 10;
    private static final char[] COLORS = {'R', 'G', 'B', 'Y', 'O', 'P'};

    private JLabel statusLabel = new JLabel("Mastermind — Guess the 4-color code!");
    private JTextField inputField = new JTextField(10);
    private JButton submitButton = new JButton("Guess");
    private JPanel boardPanel = new JPanel(new GridLayout(MAX_ATTEMPTS, 1, 3, 3));
    private JLabel[] guessLabels = new JLabel[MAX_ATTEMPTS];
    private JLabel[] feedbackLabels = new JLabel[MAX_ATTEMPTS];

    private char[] secretCode = generateCode();
    private int currentAttempt = 0;
    private boolean gameOver = false;
    private int wins = 0;
    private int losses = 0;

    public Mastermind() {
        setTitle("Mastermind — Guess the Code");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        boardPanel.setBackground(Color.DARK_GRAY);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            JLabel guessLabel = new JLabel("", SwingConstants.CENTER);
            guessLabel.setFont(new Font("Monospaced", Font.BOLD, 22));
            guessLabel.setBackground(Color.WHITE);
            guessLabel.setOpaque(true);
            guessLabel.setPreferredSize(new Dimension(200, 30));
            guessLabels[i] = guessLabel;

            JLabel feedbackLabel = new JLabel("", SwingConstants.CENTER);
            feedbackLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
            feedbackLabel.setBackground(Color.LIGHT_GRAY);
            feedbackLabel.setOpaque(true);
            feedbackLabel.setPreferredSize(new Dimension(100, 30));
            feedbackLabels[i] = feedbackLabel;

            JPanel row = new JPanel(new GridLayout(1, 2, 3, 3));
            row.add(guessLabel);
            row.add(feedbackLabel);
            boardPanel.add(row);
        }
        add(boardPanel, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel();
        inputPanel.add(new JLabel("Guess (e.g. RGBY): "));
        inputPanel.add(inputField);
        submitButton.addActionListener(e -> onSubmit());
        inputField.addActionListener(e -> onSubmit());
        inputPanel.add(submitButton);
        add(inputPanel, BorderLayout.NORTH);

        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        statusLabel.setBackground(Color.LIGHT_GRAY);
        statusLabel.setOpaque(true);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void onSubmit() {
        if (gameOver) {
            resetGame();
            return;
        }
        String input = inputField.getText().trim().toUpperCase();
        if (input.length() != CODE_LENGTH) {
            statusLabel.setText("Enter exactly " + CODE_LENGTH + " colors (R,G,B,Y,O,P)");
            return;
        }
        for (char c : input.toCharArray()) {
            if (!isValidColor(c)) {
                statusLabel.setText("Invalid color: " + c + ". Use R,G,B,Y,O,P");
                return;
            }
        }
        if (currentAttempt >= MAX_ATTEMPTS) return;

        int black = 0;
        int white = 0;
        boolean[] codeUsed = new boolean[CODE_LENGTH];
        boolean[] guessUsed = new boolean[CODE_LENGTH];

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (input.charAt(i) == secretCode[i]) {
                black++;
                codeUsed[i] = true;
                guessUsed[i] = true;
            }
        }
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < CODE_LENGTH; j++) {
                if (!codeUsed[j] && input.charAt(i) == secretCode[j]) {
                    white++;
                    codeUsed[j] = true;
                    break;
                }
            }
        }

        guessLabels[currentAttempt].setText(input);
        guessLabels[currentAttempt].setForeground(Color.BLUE);
        String fb = "";
        for (int b = 0; b < black; b++) fb += "\u25CF";
        for (int w = 0; w < white; w++) fb += "\u25CB";
        feedbackLabels[currentAttempt].setText(fb);
        feedbackLabels[currentAttempt].setForeground(new Color(80, 200, 80));

        currentAttempt++;
        inputField.setText("");

        if (black == CODE_LENGTH) {
            wins++;
            gameOver = true;
            statusLabel.setText("You win! Code was " + new String(secretCode) + ". Click Guess to play again. W:" + wins + " L:" + losses);
            setTitle("Mastermind — You Win!");
        } else if (currentAttempt >= MAX_ATTEMPTS) {
            losses++;
            gameOver = true;
            statusLabel.setText("You lose! Code was " + new String(secretCode) + ". Click Guess to play again. W:" + wins + " L:" + losses);
            setTitle("Mastermind — You Lose!");
        } else {
            statusLabel.setText("Attempt " + currentAttempt + "/" + MAX_ATTEMPTS + " — B:" + black + " W:" + white);
        }
    }

    private boolean isValidColor(char c) {
        for (char valid : COLORS) {
            if (c == valid) return true;
        }
        return false;
    }

    private void resetGame() {
        secretCode = generateCode();
        currentAttempt = 0;
        gameOver = false;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            guessLabels[i].setText("");
            feedbackLabels[i].setText("");
        }
        statusLabel.setText("New game! Guess the " + CODE_LENGTH + "-color code. W:" + wins + " L:" + losses);
        setTitle("Mastermind — Guess the Code");
        inputField.setText("");
    }

    private static char[] generateCode() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = COLORS[(int) (Math.random() * COLORS.length)];
        }
        return code;
    }

    @Deprecated
    public String getScore() {
        return "W:" + wins + " L:" + losses;
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(Mastermind::new);
    }
    public int getAttemptCount() {
        return currentAttempt;
    }
    @Override
    public String toString() {
        return "Mastermind[W:" + wins + " L:" + losses + "]";
    }
}
