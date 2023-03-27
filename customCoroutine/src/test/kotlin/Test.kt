import com.iwatchme.customcoroutine.dag.Pipeline
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.LinkedList

class Solution {

    @Test
    fun test() = runBlocking {
         Pipeline().execute()
    }

}