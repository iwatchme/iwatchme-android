package com.iwatchme.startuplauncher.mock;

import static org.junit.Assert.assertEquals;


import com.iwatchme.startuplauncher.sort.Graph;
import com.iwatchme.startuplauncher.sort.TaskSortUtils;
import com.iwatchme.startuplauncher.task.Task;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void test_graph() {
        Graph graph = new Graph(5);
        graph.addEdge(0, 3);
        graph.addEdge(0, 1);
        graph.addEdge(1, 3);
        graph.addEdge(3, 4);
        graph.addEdge(3, 2);
        graph.addEdge(1, 2);
        graph.addEdge(2, 4);

        graph.topoligicalSort().forEach(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) {
                System.out.println(integer);
            }
        });

    }

    @Test
    public void test_task_sort() {
        List<Task> originTask = new ArrayList<>();
        originTask.add(new TaskE());
        originTask.add(new TaskA());
        originTask.add(new TaskB());
        originTask.add(new TaskC());
        originTask.add(new TaskD());
        List<Class<? extends Task>> originclzClass = new ArrayList<>();
        originclzClass.add(TaskE.class);
        originclzClass.add(TaskA.class);
        originclzClass.add(TaskB.class);
        originclzClass.add(TaskC.class);
        originclzClass.add(TaskD.class);

        TaskSortUtils.Companion.getSortResult(originTask, originclzClass)
                .forEach(new Consumer<Task>() {
                    @Override
                    public void accept(Task task) {
                        System.out.println(task.getClass().getName());
                    }
                });
    }

}