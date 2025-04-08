package com.example.newpdr.Project;


import com.example.newpdr.Project.Project;

// 当前项目状态管理
public class CurrentProjectHolder {
    private static CurrentProjectHolder instance;
    private Project currentProject;

    public static synchronized CurrentProjectHolder getInstance() {
        if (instance == null) {
            instance = new CurrentProjectHolder();
        }
        return instance;
    }

    public void setCurrentProject(Project project) {
        this.currentProject = project;
    }

    public Project getCurrentProject() {
        return currentProject;
    }

    public boolean hasActiveProject() {
        return currentProject != null;
    }

    public void clearProject() {
        this.currentProject = null;
    }
}