package example.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MainClass

fun main(args: Array<String>) {
	runApplication<MainClass>(*args)
}
