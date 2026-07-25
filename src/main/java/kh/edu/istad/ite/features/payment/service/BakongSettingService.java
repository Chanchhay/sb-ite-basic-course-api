package kh.edu.istad.ite.features.payment.service;

import kh.edu.istad.ite.features.payment.dto.BakongSettingRequest;
import kh.edu.istad.ite.features.payment.dto.BakongSettingResponse;
import kh.edu.istad.ite.features.payment.dto.KhqrPreviewRequest;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;

public interface BakongSettingService {

    BakongSettingResponse getMySetting();

    BakongSettingResponse saveMySetting(BakongSettingRequest request);

    BakongSettingResponse activate();

    BakongSettingResponse deactivate();

    KhqrResponse preview(KhqrPreviewRequest request);
}
