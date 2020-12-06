package view;

import model.Game;
import proto.SnakeProto;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class MainForm extends JFrame {
    private JPanel mainPanel;
    private JButton exitButton;
    private JPanel gamePanel;
    private JTable gamesTable;
    private DefaultTableModel gamesTableModel;
    private JButton newGameButton;
    private JList<String> playersList;
    private JList<String> settingsList;
    private JScrollPane gamesTablePane;
    private JButton settingsButton;
    private final Game game;
    private final SettingsForm settingsForm; // скрывать при подключении / запуске новой игры, показывать при выходе ??

    public MainForm(SettingsForm settingsForm, Game game) {
        $$$setupUI$$$();
        this.game = game;
        this.setContentPane(mainPanel);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        gamePanel.setBackground(Color.GRAY);
        exitButton.addActionListener(actionEvent -> game.changeToViewer());
        playersList.setVisibleRowCount(-1);
        this.pack();
        this.settingsForm = settingsForm;
        settingsForm.setLocation(settingsButton.getX(), settingsButton.getY());
        settingsButton.addActionListener(actionEvent
                -> settingsForm.setVisible(true));
        this.setVisible(true);

        newGameButton.addActionListener(actionEvent -> {
            settingsForm.saveSettings();
            game.startNewGame(getGamePanel(), settingsForm.getGameConfig(), settingsForm.getPlayerName());
        });
    }

    public void setNewOrUpdateGame(String name, Integer number, String size, String food) {
        JButton enterButton = new JButton("->");
        Object[] data = new Object[]{name, number, size, food, enterButton};
        for (int i = 0; i < gamesTableModel.getRowCount(); i++) {
            if (gamesTableModel.getValueAt(i, 1) == number) {
                for (int j = 0; j < gamesTableModel.getColumnCount(); j++) {
                    if (gamesTableModel.getValueAt(i, j) != data[j]) {
                        gamesTableModel.setValueAt(data[j], i, j);
                    }
                }
                return;
            }
        }
        gamesTableModel.insertRow(0, new Object[]{name, number, size, food, enterButton});
    }

    public void deleteGame(Integer gameId) {
        for (int i = 0; i < gamesTableModel.getRowCount(); i++) {
            if (gamesTableModel.getValueAt(i, 1).equals(gameId)) {
                gamesTableModel.removeRow(i);
            }
        }
    }

    public void displayGameConfig(SnakeProto.GameConfig gameConfig, String hostName) {
        settingsList.setListData(new String[]
                {
                        "Host: " + hostName,
                        "Size: " + gameConfig.getWidth() + "x" + gameConfig.getHeight(),
                        "Food: " + gameConfig.getFoodStatic() + "+" + gameConfig.getFoodPerPlayer() + "x"
                });
    }

    public void displayScore(Map<SnakeProto.GamePlayer, Integer> players, int playerId) {
        Color[] colors = getGamePanel().getColors();
        colors[0] = Color.BLACK;

        Stream<Map.Entry<SnakeProto.GamePlayer, Integer>> sorted =
                players.entrySet().stream()
                        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()));

        List<String> scoreList = new ArrayList<>();
        Color[] snakeColor = new Color[players.size()];
        AtomicInteger place = new AtomicInteger();
        sorted.forEach((playerEntry) -> {
            snakeColor[place.get()] = colors[playerEntry.getKey().getId() % colors.length];
            place.getAndIncrement();
            scoreList.add(String.format("%-45s %s",
                    (playerEntry.getKey().getId() == playerId ? "(You) " : "") + playerEntry.getKey().getName(),
                    playerEntry.getKey().getScore()));
        });
        setPlayersListColors(snakeColor);
        playersList.setListData(scoreList.toArray(String[]::new));
    }

    private void setPlayersListColors(Color[] snakeColor) {
        playersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setForeground(snakeColor[index]);
                return c;
            }
        });
    }

    public GamePanel getGamePanel() {
        return (GamePanel) gamePanel;
    }

    public void setGamePanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        mainPanel = new JPanel();
        mainPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), 5, -1));
        gamePanel.setEnabled(true);
        gamePanel.setVisible(true);
        mainPanel.add(gamePanel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 3, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(100, 100), new Dimension(400, 400), null, 1, false));
        playersList = new JList();
        mainPanel.add(playersList, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 2, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        settingsList = new JList();
        mainPanel.add(settingsList, new com.intellij.uiDesigner.core.GridConstraints(0, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(150, 100), null, 0, false));
        gamesTablePane = new JScrollPane();
        mainPanel.add(gamesTablePane, new com.intellij.uiDesigner.core.GridConstraints(2, 1, 1, 3, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTH, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 100), null, 0, false));
        gamesTablePane.setViewportView(gamesTable);
        settingsButton = new JButton();
        settingsButton.setText("Settings");
        mainPanel.add(settingsButton, new com.intellij.uiDesigner.core.GridConstraints(1, 3, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTH, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(80, 40), null, 0, false));
        newGameButton = new JButton();
        newGameButton.setHorizontalAlignment(0);
        newGameButton.setText("New Game");
        mainPanel.add(newGameButton, new com.intellij.uiDesigner.core.GridConstraints(1, 1, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHEAST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(60, 40), null, 0, false));
        exitButton = new JButton();
        exitButton.setHorizontalAlignment(0);
        exitButton.setHorizontalTextPosition(0);
        exitButton.setText("Exit");
        exitButton.setVerticalAlignment(0);
        exitButton.setVerticalTextPosition(0);
        mainPanel.add(exitButton, new com.intellij.uiDesigner.core.GridConstraints(1, 2, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(84, 40), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

    private void createUIComponents() {
        gamePanel = new GamePanel();

        gamesTableModel =
                new DefaultTableModel(new String[]{"Host", "#", "Size", "Food", "Enter"}, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
        gamesTable = new JTable(gamesTableModel);

        // рендер кнопки в таблице
        gamesTable.getColumn("Enter")
                .setCellRenderer((table, value, isSelected, hasFocus, row, column) -> (JButton) value);

        gamesTable.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (gamesTable.getSelectedColumn() == 4) {
                    settingsForm.saveSettings();
                    int hostId = (Integer) gamesTable.getValueAt(gamesTable.getSelectedRow(), 1);
                    game.joinGame(getGamePanel(), settingsForm.getPlayerName(), hostId, SnakeProto.NodeRole.NORMAL);
                } else {
                    settingsForm.saveSettings();
                    int hostId = (Integer) gamesTable.getValueAt(gamesTable.getSelectedRow(), 1);
                    game.joinGame(getGamePanel(), settingsForm.getPlayerName(), hostId, SnakeProto.NodeRole.VIEWER);
                }
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });
    }
}