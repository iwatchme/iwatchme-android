import java.util.LinkedList

class Test {


    fun canReach(arr: IntArray, start: Int): Boolean {
        val visited = BooleanArray(arr.size)
        val queue = LinkedList<Int>()

        queue.add(start)
        visited[start] = true

        while (!queue.isEmpty()) {
            val index = queue.poll()
            if (arr[index] == 0) {
                return true
            }

            val left = index - arr[index]
            if (left >= 0 && !visited[left]) {
                if (arr[left] == 0) {
                    return true
                }
                queue.add(left)
                visited[left] = true
            }

            val right = index + arr[index]
            if (right < arr.size && !visited[right]) {
                if (arr[right] == 0) {
                    return true
                }
                queue.add(right)
                visited[right] = true
            }
        }

        return false

    }
}