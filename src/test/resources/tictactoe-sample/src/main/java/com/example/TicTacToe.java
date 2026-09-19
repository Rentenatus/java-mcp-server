package com.example;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TicTacToe extends JFrame {
    private JLabel[][] cells = new JLabel[3][3];
    private JLabel statusLabel = new JLabel("Player X's turn");
    private char currentPlayer = 'X';

    public TicTacToe() {
        setTitle("Tic-Tac-Toe Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initUI() {
        JPanel board = new JPanel(new GridLayout(3, 3, 5, 5));
        board.setBackground(Color.DARK_GRAY);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                JLabel label = new JLabel("", SwingConstants.CENTER);
                label.setFont(new Font("SansSerif", Font.BOLD, 60));
                label.setBackground(Color.WHITE);
                label.setOpaque(true);
                label.setPreferredSize(new Dimension(100, 100));
                final int row = r;
                final int col = c;
                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onCellClick(row, col);
                    }
                });
                cells[r][c] = label;
                board.add(label);
            }
        }
        add(board, BorderLayout.CENTER);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        statusLabel.setBackground(Color.LIGHT_GRAY);
        statusLabel.setOpaque(true);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void onCellClick(int row, int col) {
        if (!cells[row][col].getText().isEmpty()) {
            return;
        }
        placeMark(row, col);
        if (checkWin()) {
            statusLabel.setText("Player " + currentPlayer + " wins!");
            setTitle("Player " + currentPlayer + " wins!");
            resetBoard();
        } else if (isBoardFull()) {
            statusLabel.setText("Draw! Click to play again.");
            setTitle("Tic-Tac-Toe Game - Draw");
            resetBoard();
        } else {
            currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
            statusLabel.setText("Player " + currentPlayer + "'s turn");
            setTitle("Tic-Tac-Toe Game - Player " + currentPlayer);
        }
    }

    private void placeMark(int row, int col) {
        JLabel label = cells[row][col];
        label.setText(String.valueOf(currentPlayer));
        if (currentPlayer == 'X') {
            label.setForeground(Color.RED);
        } else {
            label.setForeground(Color.BLUE);
        }
    }

    private boolean checkWin() {
        String p = String.valueOf(currentPlayer);
        for (int i = 0; i < 3; i++) {
            if (cells[i][0].getText().equals(p) && cells[i][1].getText().equals(p) && cells[i][2].getText().equals(p)) return true;
            if (cells[0][i].getText().equals(p) && cells[1][i].getText().equals(p) && cells[2][i].getText().equals(p)) return true;
        }
        if (cells[0][0].getText().equals(p) && cells[1][1].getText().equals(p) && cells[2][2].getText().equals(p)) return true;
        return cells[0][2].getText().equals(p) && cells[1][1].getText().equals(p) && cells[2][0].getText().equals(p);
    }

    private void resetBoard() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells[r][c].setText("");
            }
        }
        currentPlayer = 'X';
        statusLabel.setText("Player X's turn");
        setTitle("Tic-Tac-Toe Game");
    }

    private boolean isBoardFull() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (cells[r][c].getText().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(TicTacToe::new);
    }
}
