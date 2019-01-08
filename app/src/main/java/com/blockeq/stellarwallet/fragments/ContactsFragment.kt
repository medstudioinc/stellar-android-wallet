package com.blockeq.stellarwallet.fragments

import android.Manifest
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.blockeq.stellarwallet.R
import com.blockeq.stellarwallet.activities.StellarAddressActivity
import com.blockeq.stellarwallet.adapters.ContactsAdapter
import com.blockeq.stellarwallet.helpers.OnTextChanged
import com.blockeq.stellarwallet.interfaces.OnSearchStateListener
import com.blockeq.stellarwallet.models.Contact
import com.blockeq.stellarwallet.views.RecyclerSectionItemDecoration
import com.blockeq.stellarwallet.vmodels.ContactsRepositoryImpl
import kotlinx.android.synthetic.main.fragment_contact_list.*
import timber.log.Timber

/**
 * Fragment that holds the RecyclerView
 */
class ContactsFragment : Fragment() {
    companion object {
        // Request code for READ_CONTACTS. It can be any number > 0.
        private const val PERMISSIONS_REQUEST_CONTACTS = 100
    }

    // Defines a variable for the search string
    private lateinit var appContext : Context
    private var currentContactList = ArrayList<Contact>()
    private var stickyHeaderAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    // A UI Fragment must inflate its View
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_contact_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appContext = view.context.applicationContext
        activity?.let {
            (it as AppCompatActivity).setSupportActionBar(toolBar)
            rv_contact_list.layoutManager =  LinearLayoutManager(it)
            rv_contact_list.addItemDecoration(DividerItemDecoration(rv_contact_list.context, DividerItemDecoration.VERTICAL))
        }

        setInitialState()
        requestContacts()
        searchBar.addTextChangeListener(object:OnTextChanged() {
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                filterResults(text.toString())
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu to use in the action bar
        inflater.inflate(R.menu.contacts_fragment_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            R.id.refresh -> {
                setInitialState()
                showContacts(true)
                return true
            }
            R.id.add -> {
                activity?.let {
                    startActivity(StellarAddressActivity.createContact(it))
                }
                return true
            }
            R.id.search -> {
                viewFlipper.showNext()
                searchBar.enableSearch()
                searchBar.setOnSearchActionListener(object: OnSearchStateListener() {
                    override fun onSearchStateChanged(enabled: Boolean) {
                        if (!enabled) {
                            viewFlipper.showPrevious()
                        }
                    }
                })
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun setInitialState() {
        rv_contact_list.visibility = View.GONE
        empty_view.visibility = View.GONE
        progress_view.visibility = View.VISIBLE
    }


    private fun requestContacts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasPermissions(appContext, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), PERMISSIONS_REQUEST_CONTACTS)
        } else {
            showContacts()
        }
    }


    private fun hasPermissions(context: Context, vararg permissions: String): Int {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return PackageManager.PERMISSION_DENIED
            }
        }
        return  PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CONTACTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showContacts()
            } else {
                Timber.e("Permissions Access denied")
            }
        }
    }

    private fun showContacts(forceRefresh: Boolean = false) {
        ContactsRepositoryImpl(appContext).getContactsListLiveData(forceRefresh).observe(viewLifecycleOwner, Observer {
            Timber.d("Observer triggered {${it?.stellarContacts?.size}")
            it?.let { that ->
                currentContactList = ArrayList(that.contacts)
                currentContactList.addAll(0, that.stellarContacts)
                populateList(currentContactList)
            }
        })
    }

    private fun filterResults(input : String) {
        val filterList : ArrayList<Contact> = ArrayList()
        currentContactList.forEach {
            it.name?.let { name ->
                if (name.toLowerCase().
                        contains(input.toLowerCase())) {
                    filterList.add(it)
                }
            }
        }
        if (!filterList.isEmpty()) {
            populateList(filterList, true)
        }
    }


    private fun populateList(list : ArrayList<Contact>, isFilteredList : Boolean = false) {
        rv_contact_list.adapter = ContactsAdapter(list)
        if (stickyHeaderAdded) {
            // 0 position is the DividerItemDecoration
            rv_contact_list.removeItemDecorationAt(1)
        }
        val item = RecyclerSectionItemDecoration(appContext.resources.getDimension(R.dimen.contact_header).toInt(), true, getSectionCallback(list))
        rv_contact_list.addItemDecoration(item)
        stickyHeaderAdded = true
        progress_view.visibility = View.GONE
        if (list.size == 0) {
            if(isFilteredList) {
                empty_view.text = getString(R.string.no_results_found)
            } else {
                empty_view.text = getString(R.string.no_contacts_found)
            }
            empty_view.visibility = View.VISIBLE
            rv_contact_list.visibility = View.GONE
        } else {
            empty_view.visibility = View.GONE
            rv_contact_list.visibility = View.VISIBLE
        }
    }


    private fun getSectionCallback(list: List<Contact>): RecyclerSectionItemDecoration.SectionCallback {
        return object : RecyclerSectionItemDecoration.SectionCallback {
            override fun isSection(position: Int): Boolean {
                if (position == 0) return true
                val addressIsEmpty = list[position].stellarAddress.isNullOrEmpty()
                val previousAddressIsEmpty = list[position-1].stellarAddress.isNullOrEmpty()
                return addressIsEmpty xor previousAddressIsEmpty
            }

            override fun getSectionHeader(position: Int): CharSequence {
                return if (list[position].stellarAddress.isNullOrBlank()) {
                    "ADDRESS BOOK"
                } else {
                    return "STELLAR CONTACT"
                }
            }
        }
    }

}
