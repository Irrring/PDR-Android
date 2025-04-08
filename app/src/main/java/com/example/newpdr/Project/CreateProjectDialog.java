package com.example.newpdr.Project;


import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.newpdr.R;

public class CreateProjectDialog {
    public interface OnProjectCreated {
        void onCreated(String projectName);
    }

    private final Context context;
    private final OnProjectCreated callback;

    public CreateProjectDialog(Context context, OnProjectCreated callback) {
        this.context = context;
        this.callback = callback;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_create_project, null);
        EditText editName = view.findViewById(R.id.edit_project_name);

        builder.setView(view)
                .setTitle("创建项目")
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        callback.onCreated(name);
                    } else {
                        Toast.makeText(context, "请输入项目名称", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create()
                .show();
    }
}