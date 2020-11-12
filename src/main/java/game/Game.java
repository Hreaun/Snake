package game;

import controller.Controller;
import model.Model;
import model.Snake;
import view.MainForm;

import java.util.Observer;

public class Game {
    public Game() {
        Snake snake = new Snake();
        Controller controller = new Controller(snake);
        MainForm mainForm = new MainForm();
        mainForm.getGamePanel().setSnake(snake);
        Model model = new Model(snake, (Observer) mainForm.getGamePanel());
        model.start(100);
    }
}
