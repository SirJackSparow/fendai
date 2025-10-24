package com.dg.fendai.implement

import android.content.Context
import androidx.startup.Initializer

class FendAIInitializer : Initializer<MainFendAI> {

    /**
     * Initializes the [MainFendAI] singleton with the application [Context].
     * This method is called by the App Startup library to create and initialize
     * the component.
     *
     * @param context The application context provided by the App Startup library.
     * @return The initialized [MainFendAI] singleton instance.
     */
    override fun create(context: Context): MainFendAI {
        MainFendAI.init(context)
        println("Init FendAI successfully")
        return MainFendAI
    }

    /**
     * Specifies any other Initializers that this one depends on.
     * The App Startup library uses this to determine the order in which to run the initializers.
     *
     * For example, if FendAIInitializer needed another library, say "LoggingInitializer", to be
     * set up first, you would return it here like this:
     * `return listOf(LoggingInitializer::class.java)`
     *
     * In this case, this initializer has no dependencies, so it can be run at any time.
     * The "TODO" indicates this is not yet implemented and should be replaced with an empty list
     * if there are truly no dependencies.
     *
     * @return A list of `Initializer` classes that must be initialized before this one.
     */
    override fun dependencies(): List<Class<out Initializer<*>?>?> = emptyList()

}