#!/usr/bin/env python3
# Generates the Kotlin corpora the parser benchmarks run on (see README.md).
#
#   generate_corpora.py <output directory> <test-projects directory>
#
# small       the Kotlin files of the test projects (real code, little of it)
# medium      500 generated Spring style files, short function bodies
# large       5000 of them
# hugefile    one file of 13k lines, short function bodies
# bodies      600 generated files with function bodies of about 25 lines
# bodieshuge  one file of 13k lines with function bodies of about 25 lines
import glob
import os
import shutil
import sys


def short_bodies(i):
    return f'''package com.example.module{i % 40}.feature{i % 7}

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * Entity number {i}.
 */
data class Item{i}(val id: Long, val name: String, val tags: List<String> = emptyList(), val owner: String? = null)

enum class Status{i} {{ NEW, ACTIVE, DONE; fun isOpen() = this != DONE }}

interface Repository{i} {{
    fun find(id: Long): Item{i}?
    fun findAll(): List<Item{i}>
    fun save(item: Item{i}): Item{i}
}}

/** Service for item {i}. */
@Service
class Service{i}(private val repository: Repository{i}) {{

    companion object {{
        const val PREFIX = "/api/items{i}"
        const val ADMIN = PREFIX + "/admin"
    }}

    // look up one
    @Transactional(readOnly = true)
    fun find(id: Long): Item{i}? = repository.find(id)

    @Transactional
    fun save(item: Item{i}): Item{i} {{
        // validate first
        require(item.name.isNotBlank()) {{ "name must not be blank" }}
        return repository.save(item)
    }}

    fun search(text: String, limit: Int = 10): List<Item{i}> {{
        return repository.findAll().filter {{ it.name.contains(text) }}.take(limit)
    }}
}}

/**
 * The controller for items {i}.
 */
@RestController
@RequestMapping("/api/items{i}", produces = [MediaType.APPLICATION_JSON_VALUE])
class Controller{i}(private val service: Service{i}) {{

    @Autowired
    lateinit var other: Service{i}

    @GetMapping("/{{id}}")
    fun item(@PathVariable id: Long, @RequestParam(required = false) filter: String?): Item{i}? = service.find(id)

    @GetMapping
    fun list(@RequestParam("q", defaultValue = "") q: String): List<Item{i}> {{
        return service.search(q)
    }}

    @PostMapping(value = ["/create", "/new"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody item: Item{i}): Item{i} {{
        return service.save(item)
    }}

    @PutMapping("/{{id}}")
    fun update(@PathVariable id: Long, @RequestBody item: Item{i}) = service.save(item.copy(id = id))

    @DeleteMapping("/{{id}}")
    fun delete(@PathVariable id: Long) {{
        println("delete $id from ${{javaClass.simpleName}}")
    }}
}}

@Configuration
class Config{i} {{
    @Bean
    fun repository{i}(): Repository{i} = object : Repository{i} {{
        private val items = mutableMapOf<Long, Item{i}>()
        override fun find(id: Long) = items[id]
        override fun findAll() = items.values.toList()
        override fun save(item: Item{i}): Item{i} {{ items[item.id] = item; return item }}
    }}
}}
'''


def long_body(i, k):
    return f'''        // step one of {k}
        val items = repository.findAll().filter {{ it.name.contains(text) && it.id > {k} }}
        val grouped = items.groupBy {{ it.owner ?: "unknown" }}.mapValues {{ (_, list) -> list.map {{ it.name.uppercase() }} }}
        if (grouped.isEmpty()) {{
            logger.info("nothing found for $text in ${{items.size}} items of {i}")
            return emptyList()
        }}
        for ((owner, names) in grouped) {{
            when {{
                names.size > 10 -> logger.warn("many names for $owner: ${{names.take(3)}}")
                names.isEmpty() -> continue
                else -> names.forEachIndexed {{ index, name -> println("$index: $name for ${{owner.length}}") }}
            }}
        }}
        val result = try {{
            items.map {{ Item{i}(it.id, it.name + "-" + {k}, it.tags + listOf("x", "y"), it.owner) }}
        }} catch (e: IllegalStateException) {{
            logger.error("failed: ${{e.message}}", e)
            throw RuntimeException("wrapped", e)
        }}
        /* done with {k} */
        return result.sortedBy {{ it.name }}.take(limit)'''


def long_bodies(i, functions=8):
    queries = ''.join(f'''
    @Transactional(readOnly = true)
    fun query{k}(text: String, limit: Int = 10): List<Item{i}> {{
{long_body(i, k)}
    }}
''' for k in range(functions))
    return f'''package com.example.module{i % 40}.feature{i % 7}

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class Item{i}(val id: Long, val name: String, val tags: List<String> = emptyList(), val owner: String? = null)

interface Repository{i} {{
    fun findAll(): List<Item{i}>
}}

/** Service {i}. */
@Service
class Service{i}(private val repository: Repository{i}) {{
    private val logger = LoggerFactory.getLogger(Service{i}::class.java)
{queries}}}
'''


def write_files(directory, count, generator):
    shutil.rmtree(directory, ignore_errors=True)
    os.makedirs(directory)
    for i in range(count):
        with open(f'{directory}/File{i}.kt', 'w') as f:
            f.write(generator(i))


def one_file(directory, generator, parts, header):
    shutil.rmtree(directory, ignore_errors=True)
    os.makedirs(directory)
    body = '\n'.join(generator(i).split('\n\n', 1)[1] for i in range(parts))
    # one package and one list of imports for the whole file
    body = '\n'.join(line for line in body.split('\n') if not line.startswith('import '))
    with open(f'{directory}/Huge.kt', 'w') as f:
        f.write(header + body)


def main():
    out, projects = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)

    small = f'{out}/small'
    shutil.rmtree(small, ignore_errors=True)
    os.makedirs(small)
    for n, path in enumerate(sorted(glob.glob(f'{projects}/*kotlin*/src/**/*.kt', recursive=True))):
        shutil.copy(path, f'{small}/{n}_{os.path.basename(path)}')

    write_files(f'{out}/medium', 500, short_bodies)
    write_files(f'{out}/large', 5000, short_bodies)
    write_files(f'{out}/bodies', 600, long_bodies)

    imports = ''.join(f'import {i}\n' for i in [
        'org.springframework.web.bind.annotation.*', 'org.springframework.stereotype.Service', 'org.springframework.context.annotation.*',
        'org.springframework.http.*', 'org.springframework.transaction.annotation.Transactional',
        'org.springframework.beans.factory.annotation.Autowired', 'org.slf4j.LoggerFactory'])
    header = 'package com.example.huge\n\n' + imports + '\n'
    one_file(f'{out}/hugefile', short_bodies, 150, header)
    one_file(f'{out}/bodieshuge', long_bodies, 60, header)


if __name__ == '__main__':
    main()
