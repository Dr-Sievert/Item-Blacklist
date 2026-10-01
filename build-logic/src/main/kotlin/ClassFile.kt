import java.io.DataInputStream
import org.gradle.api.GradleException

/**
 * A class file's constant pool, read from the start of the stream: checks the magic and
 * reads the version, then the pool, leaving the stream at access_flags. One parser for both
 * checks (checkBinaryPortable, checkLazyVersionClasses), so they cannot drift.
 */
class ConstantPool(input: DataInputStream) {
    private val strings: Array<String?>

    /** "owner.name descriptor" of every Fieldref, Methodref and InterfaceMethodref. */
    val memberRefs: Set<String>

    init {
        if (input.readInt() != 0xCAFEBABE.toInt()) throw GradleException("not a class file")
        input.readUnsignedShort() // minor_version
        input.readUnsignedShort() // major_version
        val count = input.readUnsignedShort()
        strings = arrayOfNulls(count)
        val classIndex = IntArray(count)
        val nameAndType = arrayOfNulls<IntArray>(count)
        val members = mutableListOf<IntArray>()
        fun indexPair() = intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
        var i = 1
        while (i < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> strings[i] = input.readUTF()
                3, 4 -> input.readInt()
                5, 6 -> { input.readLong(); i++ } // a long or double takes two entries
                7 -> classIndex[i] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.readUnsignedShort()
                9, 10, 11 -> members += indexPair()
                12 -> nameAndType[i] = indexPair()
                15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                17, 18 -> input.readInt()
                else -> throw GradleException("unknown constant pool tag $tag")
            }
            i++
        }
        memberRefs = members.map { (owner, nat) ->
            val (name, descriptor) = nameAndType[nat]!!
            "${strings[classIndex[owner]]}.${strings[name]} ${strings[descriptor]}"
        }.toSet()
    }

    fun utf8(index: Int): String? = strings[index]
}
