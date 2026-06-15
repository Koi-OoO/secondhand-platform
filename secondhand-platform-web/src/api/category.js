import http from './index'

/**
 * 获取全部分类（树形结构，顶级分类嵌套子分类）
 * 结果通过 Redis 缓存 30 分钟
 */
export function getCategories() {
  return http.get('/category/list')
}
