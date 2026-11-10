package org.test

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestMethod.PUT
import org.springframework.stereotype.Controller

@Controller
class RequestMethodClass {

	@GetMapping("/getData")
	fun getData() {
	}

	@PutMapping("/putData")
	fun putData() {
	}

	@DeleteMapping("/deleteData")
	fun deleteData() {
	}

	@PostMapping("/postData")
	fun postData() {
	}

	@PatchMapping("/patchData")
	fun patchData() {
	}

	@RequestMapping(value = ["/getHello"], method = [RequestMethod.GET])
	fun getHello() {
	}

	@RequestMapping(path = ["/postAndPutHello"], method = [RequestMethod.POST, PUT])
	fun updateHello() {
	}

	@GetMapping("")
	fun getDataWithoutPath() {
	}

	@GetMapping()
	fun getDataWithoutAnything() {
	}

}
