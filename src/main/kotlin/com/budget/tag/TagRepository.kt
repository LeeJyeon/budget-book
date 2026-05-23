package com.budget.tag

import com.budget.common.TagType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TagRepository : JpaRepository<Tag, Long> {
    fun findAllByOrderByTypeAscNameAsc(): List<Tag>

    fun findByNameAndType(name: String, type: TagType): Tag?

    @Query(
        """
        SELECT t FROM Tag t
        WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY t.name ASC
        """
    )
    fun search(@Param("q") q: String): List<Tag>
}
