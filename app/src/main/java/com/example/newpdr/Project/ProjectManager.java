package com.example.newpdr.Project;



import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.newpdr.DataClass.AccelData;
import com.example.newpdr.DataClass.GyroData;
import com.example.newpdr.DataClass.MagData;
import com.example.newpdr.DataClass.PressureData;
import com.example.newpdr.ViewModel.SensorViewModel;
import com.example.newpdr.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import com.example.newpdr.Project.ProjectDataManager;
import com.example.newpdr.utils.SettingsManager;

// 项目管理
public class ProjectManager {
    private static final String TAG = "ProjectManager";
    private static ProjectManager instance;
    private List<Project> projects = new ArrayList<>();
    private final Context context;
    private ProjectDataManager dataManager;
    public static synchronized ProjectManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProjectManager(context.getApplicationContext());
        }
        return instance;
    }

    public ProjectManager(Context context) {
        this.context = context;
        loadProjects();
    }

    private void loadProjects() {
        projects.clear();
        File projectsRoot = new File(context.getExternalFilesDir(null), "Projects");
        if (!projectsRoot.exists()) return;

        for (File projectDir : projectsRoot.listFiles()) {
            if (projectDir.isDirectory()) {
                try {
                    Project project = new Project();
                    project.setProjectDir(projectDir);

                    // 初始化 ProjectDataManager
                    dataManager = new ProjectDataManager(projectDir);

                    // 从meta.json加载元数据
                    JSONObject meta = dataManager.loadProjectMeta();
                    if (meta != null) {
                        project.setProjectId(meta.getString("projectId"));
                        project.setProjectName(meta.getString("projectName"));
                        project.setCreateTime(new Date(meta.getLong("createTime")));
                        project.setLastModified(new Date(meta.getLong("lastModified")));
                    }
                    projects.add(project);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading project: " + projectDir.getName(), e);
                }
            }
        }
    }

    public Project createNewProject(String projectName, SensorViewModel sensorViewModel,SettingsManager settings) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String dirName = projectName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp;

        Project project = new Project();
        project.setProjectId(UUID.randomUUID().toString());
        project.setProjectName(projectName);

        // 获取外部存储公共目录，存储项目文件
        File externalStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        // 创建项目目录
        File projectDir = new File(externalStorageDir, "Projects/" + dirName);
        // 确保目录存在
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }
        project.setProjectDir(projectDir);
        // 目录在Documents下，例如Project directory path: /storage/emulated/0/Documents/Projects/mmm_20250408_194249

        // 初始化 ProjectDataManager
        dataManager = new ProjectDataManager(projectDir);

        if (createProjectStructure(project)) {
            // 使用 ProjectDataManager 保存元数据等
            saveProjectData(project,sensorViewModel,settings);
            projects.add(project);
            return project;
        }

        return null;
    }

    private boolean createProjectStructure(Project project) {
        try {
            File projectDir = project.getProjectDir();
            Log.d(TAG, "Project directory path: " + projectDir.getAbsolutePath());

            // 检查路径是否有效
            if (projectDir.exists() || projectDir.mkdirs()) {
                new File(projectDir, "sensor_data").mkdirs();
                new File(projectDir, "map_data").mkdirs();
                new File(projectDir, "config").mkdirs();
                return true;
            } else {
                Log.e(TAG, "Failed to create project directory.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Create project failed", e);
            return false;
        }
    }

    // 新增更新保存方法,包括Meta
    public void saveProjectData(Project project, SensorViewModel sensorViewModel, SettingsManager settings) {
        dataManager = new ProjectDataManager(project.getProjectDir());
        dataManager.saveAllSensorData(sensorViewModel);
        dataManager.saveProjectMeta(project);
        dataManager.saveConfigData(settings);
    }

    public List<Project> getAllProjects() {
        return new ArrayList<>(projects);
    }

    public void deleteProject(Project project) {
        try {
            deleteRecursive(project.getProjectDir());
            projects.remove(project);
        } catch (Exception e) {
            Log.e(TAG, "Delete project failed", e);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteRecursive(child);
            }
        }
        file.delete();
    }
}
