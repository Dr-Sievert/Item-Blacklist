import java.io.File
import java.io.Serializable
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec

/**
 * Selects the class files compiled from a set of source folders: a class file belongs to
 * the top-level class whose .java file sits in one of them (Foo$1.class comes from
 * Foo.java). A version module compiles everything valid at its version in one compile, the
 * check; this picks its own folders' classes out of that output, the only ones it ships. One
 * top-level class per file, as everywhere in the project.
 */
class OwnClasses(private val sourceDirs: List<File>) : Spec<FileTreeElement>, Serializable {
    override fun isSatisfiedBy(element: FileTreeElement): Boolean {
        if (element.isDirectory) return true
        val path = element.relativePath.pathString
        if (!path.endsWith(".class")) return false
        val topLevel = path.removeSuffix(".class").substringBefore('$')
        return sourceDirs.any { File(it, "$topLevel.java").isFile }
    }
}
