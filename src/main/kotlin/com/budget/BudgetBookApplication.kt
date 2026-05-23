package com.budget

import com.budget.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = [AppProperties::class])
class BudgetBookApplication

fun main(args: Array<String>) {
    runApplication<BudgetBookApplication>(*args)
}
