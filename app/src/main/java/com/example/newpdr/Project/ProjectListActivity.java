package com.example.newpdr.Project;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.newpdr.R;
import com.example.newpdr.Project.Project;
import com.example.newpdr.Project.ProjectManager;
import java.util.List;

//  项目列表界面
public class ProjectListActivity extends AppCompatActivity {
    private ProjectManager projectManager;
    private List<Project> projects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        projectManager = ProjectManager.getInstance(this);
        refreshProjectList();

    }

    private void refreshProjectList() {
        projects = projectManager.getAllProjects();
        ListView listView = findViewById(R.id.project_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                getProjectNames());

        listView.setAdapter(adapter);

        // 单击切换项目
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Project selectedProject = projects.get(position);
            switchProject(selectedProject);
        });

        // 添加长按删除
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("删除项目")
                    .setMessage("确定删除 " + projects.get(position).getProjectName() + "?")
                    .setPositiveButton("删除", (d, w) -> {
                        Project toDelete = projects.get(position);

                        // 如果是当前选中项目则清除
                        if (CurrentProjectHolder.getInstance().getCurrentProject() == toDelete) {
                            CurrentProjectHolder.getInstance().clearProject();
                        }

                        // 执行删除
                        projectManager.deleteProject(toDelete);
                        refreshProjectList();

                        Toast.makeText(this, "已删除: " + toDelete.getProjectName(), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    // 切换项目
    private void switchProject(Project selectedProject) {
        CurrentProjectHolder.getInstance().setCurrentProject(selectedProject);
        setResult(RESULT_OK); // 添加返回结果
        finish(); // 返回主界面
    }
    private String[] getProjectNames() {
        String[] names = new String[projects.size()];
        for (int i = 0; i < projects.size(); i++) {
            names[i] = projects.get(i).getProjectName();
        }
        return names;
    }


}
