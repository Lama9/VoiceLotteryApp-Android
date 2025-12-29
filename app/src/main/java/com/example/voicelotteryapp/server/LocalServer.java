package com.example.voicelotteryapp.server;

import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * 本地 HTTP 服务器
 * 基于 NanoHTTPD 实现，负责监听局域网内的 HTTP 请求。
 * 用于实现“远程控制”功能，允许外部设备控制抽奖流程。
 */
public class LocalServer extends NanoHTTPD {
    private static final String TAG = "LocalServer";
    private ServerCallback mCallback;

    /**
     * 服务器请求回调接口
     * 将解析后的请求动作抛给 Activity 处理
     */
    public interface ServerCallback {
        JSONObject onRemoteStart(); // 处理开始请求
        JSONObject onRemoteStop();  // 处理停止请求
        JSONObject onGetStatus();   // 处理状态查询请求
    }

    public LocalServer(int port, ServerCallback callback) {
        super(port);
        this.mCallback = callback;
    }

    /**
     * 核心请求处理方法
     * 当有 HTTP 请求进来时，此方法会被 NanoHTTPD 在子线程调用
     */
    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        
        Log.d(TAG, "收到请求: " + method + " " + uri);

        JSONObject responseJson = new JSONObject();
        Response.Status status = Response.Status.OK;

        try {
            // 路由及其对应的方法处理
            if (uri.equals("/api/start") && method == Method.POST) {
                // 远程开始
                if (mCallback != null) {
                    responseJson = mCallback.onRemoteStart();
                }
            } else if (uri.equals("/api/stop") && method == Method.POST) {
                // 远程停止
               if (mCallback != null) {
                   responseJson = mCallback.onRemoteStop();
               }
            } else if (uri.equals("/api/status") && method == Method.GET) {
                // 查询状态
               if (mCallback != null) {
                   responseJson = mCallback.onGetStatus();
               }
            } else {
                // 404
                status = Response.Status.NOT_FOUND;
                responseJson.put("error", "接口不存在或方法不支持");
            }
        } catch (Exception e) {
            status = Response.Status.INTERNAL_ERROR;
            try {
                responseJson.put("error", "服务器内部错误: " + e.getMessage());
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        // 构造 JSON 响应返回
        return newFixedLengthResponse(status, "application/json", responseJson.toString());
    }
}
