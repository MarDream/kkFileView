package cn.keking.model.collaboration;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 批注模型
 * 字段与前端 collab-annotation.js 的线格式一一对应：
 * 坐标 x/y/w/h 及 points 均为相对页面元素尺寸的 0~1 归一化比例，
 * type 取值 highlight/text/underline/draw。
 * 注：前端保存批注时仍会附带 fileKey 字段，Jackson 默认忽略未知字段，无需在此映射
 *
 * @author Claude Code
 */
public class Annotation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    /** 批注类型：highlight/text/underline/draw */
    private String type;
    private int page;
    private double x;
    private double y;
    private double w;
    private double h;
    /** 自由绘制路径（仅 draw 类型） */
    private List<Point> points;
    private String color;
    /** 文本批注内容（仅 text 类型） */
    private String text;
    private String authorName;
    private Instant createdAt;

    /**
     * 自由绘制路径点（0~1 归一化坐标）
     */
    public static class Point implements Serializable {

        private static final long serialVersionUID = 1L;

        private double x;
        private double y;

        public Point() {
        }

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }
    }

    public Annotation() {
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getW() {
        return w;
    }

    public void setW(double w) {
        this.w = w;
    }

    public double getH() {
        return h;
    }

    public void setH(double h) {
        this.h = h;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
