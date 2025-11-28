package com.yourapp.test.alarm

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.util.Log

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int {
        return 1 // Only alarms fragment now
    }
    
    override fun createFragment(position: Int): Fragment {
        return try {
            when (position) {
                0 -> AlarmsFragment()
                else -> AlarmsFragment() // Default to alarms fragment
            }
        } catch (e: Exception) {
            Log.e("ViewPagerAdapter", "Error creating fragment at position $position", e)
            // Return a basic fragment or handle the error appropriately
            AlarmsFragment()
        } catch (e: Throwable) {
            Log.e("ViewPagerAdapter", "Unexpected error creating fragment at position $position", e)
            AlarmsFragment()
        }
    }
}