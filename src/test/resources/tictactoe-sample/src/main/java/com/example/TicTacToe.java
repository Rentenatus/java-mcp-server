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
    private Stone currentPlayer = new XStone();

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
                stones[r][c] = new EmptyStone();
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
        if (!stones[row][col].isEmpty()) {
            return;
        }
        placeMark(row, col);
        if (checkWin()) {
            statusLabel.setText("Player " + currentPlayer.getSymbol() + " wins!");
            setTitle("Player " + currentPlayer.getSymbol() + " wins!");
            resetBoard();
        } else if (isBoardFull()) {
            statusLabel.setText("Draw! Click to play again.");
            setTitle("Tic-Tac-Toe Game - Draw");
            resetBoard();
        } else {
            currentPlayer = (currentPlayer.getSymbol() == 'X') ? new OStone() : new XStone();
            statusLabel.setText("Player " + currentPlayer.getSymbol() + "'s turn");
            setTitle("Tic-Tac-Toe Game - Player " + currentPlayer.getSymbol());
        }
    }

    private void placeMark(int row, int col) {
        Stone stone = currentPlayer;
        stones[row][col] = stone;
        JLabel label = cells[row][col];
        label.setText(stone.getDisplayText());
        label.setForeground(stone.getColor());
    }

    private boolean checkWin() {
        char p = currentPlayer.getSymbol();
        for (int i = 0; i < 3; i++) {
            if (stones[i][0].getSymbol() == p && stones[i][1].getSymbol() == p && stones[i][2].getSymbol() == p) return true;
            if (stones[0][i].getSymbol() == p && stones[1][i].getSymbol() == p && stones[2][i].getSymbol() == p) return true;
        }
        if (stones[0][0].getSymbol() == p && stones[1][1].getSymbol() == p && stones[2][2].getSymbol() == p) return true;
        return stones[0][2].getSymbol() == p && stones[1][1].getSymbol() == p && stones[2][0].getSymbol() == p;
    }

    private void resetBoard() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                stones[r][c] = new EmptyStone();
                cells[r][c].setText("");
            }
        }
        currentPlayer = new XStone();
        statusLabel.setText("Player X's turn");
        setTitle("Tic-Tac-Toe Game");
    }

    private boolean isBoardFull() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (stones[r][c].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(TicTacToe::new);
    }

    private Stone[][] stones = new Stone[3][3];
}
