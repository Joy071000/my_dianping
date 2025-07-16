package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.dto.Result;
import com.dianping.entity.Blog;

public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门博客
     * @param current 当前
     * @return {@link Result}
     */
    Result queryHotBlog(Integer current);

    /**
     * 通过id查找博客
     * @param id id
     * @return {@link Result}
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id id
     * @return {@link Result}
     */
    Result likeBlog(Long id);

    /**
     * 查询博客点赞排行榜
     * @param id id
     * @return {@link Result}
     */
    Result queryBlogLikesById(Long id);

    /**
     * 发布博客
     * @param blog 博客
     * @return {@link Result}
     */
    Result saveBlog(Blog blog);

    /**
     * 查询
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
