package com.chentongwei.security.browser.entity;

/**
 * 封装返回的JSON格式
 *
 * @author chentongwei@bshf360.com 2018-03-26 11:31
 */
public class SimpleResponse {
    /**
     * 状态码
     */
    private int code;
    /**
     * 提示消息
     */
    private String msg;
    /**
     * 业务数据
     */
    private Object data;

    public SimpleResponse(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}