package example.application

import org.jmolecules.stereotype.Stereotype

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Stereotype
annotation class MyStereotype
