package com.mycelium.giftbox.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.bequant.common.ErrorHandler
import com.mycelium.bequant.common.loader
import com.mycelium.giftbox.client.Constants
import com.mycelium.giftbox.client.GitboxAPI
import com.mycelium.wallet.R
import com.mycelium.wallet.activity.util.toStringWithUnit
import com.mycelium.wallet.databinding.FragmentGiftboxCheckoutBinding
import com.mycelium.wapi.wallet.Util
import java.text.SimpleDateFormat
import java.util.*

class GiftCheckoutFragment : Fragment() {
    private lateinit var binding: FragmentGiftboxCheckoutBinding
    val args by navArgs<GiftCheckoutFragmentArgs>()

    val sdf by lazy {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        sdf
    }
    val viewModel: GiftCheckoutFragmentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate<FragmentGiftboxCheckoutBinding>(
            inflater,
            R.layout.fragment_giftbox_checkout,
            container,
            false
        )
            .apply {
                lifecycleOwner = this@GiftCheckoutFragment
            }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fillProduct()
        binding.btBuy.setOnClickListener {
            GitboxAPI.giftRepository.checkoutProduct(viewModel.viewModelScope,
                args.orderResponse.productCode!!,
                args.orderResponse.quantity?.toInt()!!,
                args.orderResponse.amount?.toInt()!!, "btc", success = {
                    findNavController().navigate(GiftCheckoutFragmentDirections.toCheckoutResult(it!!))
                    loader(false)
                }, error = { _, error ->
                    ErrorHandler(requireContext()).handle(error)
                    loader(false)
                }, finally = {
                    loader(false)
                })
        }
    }

    private fun fillProduct() {
        with(binding) {
            tvTitle.text = args.orderResponse.productName
            tvGiftCardAmount.text = args.orderResponse.amount
            tvExpire.text = sdf.format(args.orderResponse.payTill)
//            args.orderResponse.currencyFromInfo?.
//            tvDiscount.text =
//                """from ${product?.minimumValue} to ${product?.maximumValue}"""
        }
    }
}


class GiftCheckoutFragmentViewModel : ViewModel() {
}