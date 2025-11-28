package com.yourapp.test.alarm

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 1
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AlarmsFragment()
            else -> AlarmsFragment()
        }
    }
}