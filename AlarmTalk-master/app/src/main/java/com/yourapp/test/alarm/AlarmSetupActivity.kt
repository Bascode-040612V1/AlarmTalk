    private fun generateSafeAlarmId(): Int {
        // Use a safer method to generate alarm IDs to prevent integer overflow
        // Generate a unique ID based on current time and a random component
        val timeComponent = System.currentTimeMillis() % 1000000
        val randomComponent = (Math.random() * 1000).toInt()
        return Math.abs((timeComponent + randomComponent).toInt())
    }