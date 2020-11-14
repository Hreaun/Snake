package model;

import view.MainForm;

import java.util.Observer;

public class App {
    public App() {
        Snake snake = new Snake();
        MainForm mainForm = new MainForm();
        mainForm.getGamePanel().setSnake(snake);
        mainForm.getGamePanel().setKeyBindings();
        Game game = new Game(snake, (Observer) mainForm.getGamePanel());
        game.start(100);
    }
}
