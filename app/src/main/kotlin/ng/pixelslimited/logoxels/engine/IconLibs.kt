package ng.pixelslimited.logoxels.engine

import android.content.Context

/** Open-source icon sets packed in assets/icons (see tools/build_icons.py and LICENSES.txt there): name and one-line SVG per icon. */
object IconLibs {
    class Lib(val id: String, val title: String)

    val libs = listOf(
        Lib("lucide", "Lucide"),
        Lib("heroicons-outline", "Heroicons line"),
        Lib("heroicons-solid", "Heroicons solid"),
        Lib("phosphor-regular", "Phosphor"),
        Lib("phosphor-bold", "Phosphor bold"),
        Lib("phosphor-fill", "Phosphor fill"),
    )

    private val cache = HashMap<String, List<Pair<String, String>>>()

    fun load(ctx: Context, id: String): List<Pair<String, String>> = cache.getOrPut(id) {
        try {
            ctx.assets.open("icons/$id.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { l ->
                    val i = l.indexOf('\t')
                    if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
                }.toList()
            }
        } catch (e: java.io.IOException) {
            emptyList()
        }
    }

    /** Icons whose name contains every word of [q]; hyphens in names count as spaces. */
    fun search(list: List<Pair<String, String>>, q: String): List<Pair<String, String>> {
        val words = q.trim().lowercase().split(' ', '-').filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { (n, _) -> words.all { n.replace('-', ' ').contains(it) } }
    }
}
