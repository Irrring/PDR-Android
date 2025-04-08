package com.example.newpdr.Project;


import java.io.File;
import java.util.Date;

// Project 元数据模型
public class Project {
    private String projectId;
    private String projectName;
    private Date createTime;
    private Date lastModified;
    private File projectDir;

    public Project() {
        this.createTime = new Date();
        this.lastModified = new Date();
    }

    // Getters & Setters
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getLastModified() { return lastModified; }
    public void setLastModified(Date lastModified) { this.lastModified = lastModified; }

    public File getProjectDir() { return projectDir; }
    public void setProjectDir(File projectDir) { this.projectDir = projectDir; }
}