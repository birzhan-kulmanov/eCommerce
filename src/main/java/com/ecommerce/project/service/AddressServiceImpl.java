package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.UserRepository;
import com.ecommerce.project.util.AuthUtil;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    private AuthUtil authUtil;

    @Override
    public AddressDTO createAddress(AddressDTO addressDTO, User user) {
        Address address = modelMapper.map(addressDTO, Address.class);

        List<Address> addressList = user.getAddresses();
        addressList.add(address);
        user.setAddresses(addressList);

        address.setUser(user);
        Address savedAddress = addressRepository.save(address);

        return modelMapper.map(savedAddress, AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getAllAddresses() {
        List<Address> addressList = addressRepository.findAll();
        List<AddressDTO> addressDTOs = addressList.stream()
                .map(address -> modelMapper.map(address, AddressDTO.class)).collect(Collectors.toList());
        return addressDTOs;
    }

    @Override
    public AddressDTO getAddressById(Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(()->new ResourceNotFoundException("Address", "addressId", addressId));

        return modelMapper.map(address, AddressDTO.class);
    }

    @Override
    public List<AddressDTO> getUserAddresses(User user){
        List<Address> addressList = user.getAddresses();
        List<AddressDTO> addressDTOS = addressList.stream().map(address ->
                modelMapper.map(address, AddressDTO.class)).collect(Collectors.toList());

        return addressDTOS;
    }

    @Override
    public AddressDTO updateAddress(Long addressId, Address newAddress){
        Address savedAddress = addressRepository.findById(addressId)
                .orElseThrow(()->new ResourceNotFoundException("Address", "addressId", addressId));

        savedAddress.setCity(newAddress.getCity());
        savedAddress.setCountry(newAddress.getCountry());
        savedAddress.setState(newAddress.getState());
        savedAddress.setStreet(newAddress.getStreet());
        savedAddress.setBuildingName(newAddress.getBuildingName());
        savedAddress.setPincode(newAddress.getPincode());

        addressRepository.save(savedAddress);

        User user = savedAddress.getUser();
        user.getAddresses().removeIf(address ->address.getAddressId().equals(addressId));
        user.getAddresses().add(savedAddress);
        userRepository.save(user);

        return modelMapper.map(savedAddress, AddressDTO.class);
    }

    @Override
    public String deleteAddress(Long addressId){
        Address address = addressRepository.findById(addressId)
                .orElseThrow(()->new ResourceNotFoundException("Address", "addressId", addressId));

        User user = address.getUser();
        user.getAddresses().removeIf(deletingAddress->deletingAddress.getAddressId().equals(addressId));
        userRepository.save(user);

        addressRepository.delete(address);
        return "Address successfully deleted";
    }
}
