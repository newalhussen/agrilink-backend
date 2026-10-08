package com.agrilink.config;

import com.agrilink.auth.AuthService;
import com.agrilink.buyer.BuyerDtos.UpdateBuyerProfileRequest;
import com.agrilink.buyer.BuyerService;
import com.agrilink.buyer.BuyerType;
import com.agrilink.common.AddressDto;
import com.agrilink.common.PhoneNumbers;
import com.agrilink.driver.DriverAvailability;
import com.agrilink.driver.DriverDtos.UpdateDriverProfileRequest;
import com.agrilink.driver.DriverService;
import com.agrilink.driver.VehicleType;
import com.agrilink.farmer.FarmerDtos.UpdateFarmerProfileRequest;
import com.agrilink.farmer.FarmerService;
import com.agrilink.farmer.FarmerType;
import com.agrilink.marketplace.ListingDtos.CreateListingRequest;
import com.agrilink.marketplace.ListingService;
import com.agrilink.marketplace.Product;
import com.agrilink.marketplace.ProductRepository;
import com.agrilink.marketplace.QualityGrade;
import com.agrilink.marketplace.Unit;
import com.agrilink.region.Region;
import com.agrilink.region.RegionRepository;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import com.agrilink.user.VerificationStatus;
import com.agrilink.wallet.PayoutMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Start-up data: the first admin account (when agrilink.bootstrap.admin-* is set) and, in the dev profile,
 * a verified farmer, buyer, driver and a few listings so clients can be exercised immediately.
 */
@Component
public class DataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrap.class);
    static final String DEMO_PASSWORD = "Demo@12345";

    private final AgriLinkProperties.Bootstrap props;
    private final UserRepository users;
    private final AuthService auth;
    private final FarmerService farmers;
    private final BuyerService buyers;
    private final DriverService drivers;
    private final ListingService listings;
    private final ProductRepository products;
    private final RegionRepository regions;
    private final TransactionTemplate tx;

    public DataBootstrap(AgriLinkProperties properties, UserRepository users, AuthService auth, FarmerService farmers,
                         BuyerService buyers, DriverService drivers, ListingService listings,
                         ProductRepository products, RegionRepository regions, TransactionTemplate tx) {
        this.props = properties.bootstrap();
        this.users = users;
        this.auth = auth;
        this.farmers = farmers;
        this.buyers = buyers;
        this.drivers = drivers;
        this.listings = listings;
        this.products = products;
        this.regions = regions;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.adminPhone() != null && !props.adminPhone().isBlank() && props.adminPassword() != null
                && !props.adminPassword().isBlank()) {
            tx.executeWithoutResult(s -> createAdminIfMissing());
        }
        if (props.seedDemoData()) {
            tx.executeWithoutResult(s -> seedDemo());
        }
    }

    private void createAdminIfMissing() {
        String phone = PhoneNumbers.normalize(props.adminPhone());
        if (users.existsByPhone(phone)) {
            return;
        }
        auth.createVerifiedUser(phone, props.adminName(), Role.ADMIN, props.adminPassword());
        log.info("Created bootstrap admin account for {}", PhoneNumbers.mask(phone));
    }

    private void seedDemo() {
        if (users.existsByPhone("+251911000001")) {
            return;
        }
        log.info("Seeding demo data (dev profile)");
        UUID oromia = region("OR");
        UUID addis = region("AA");

        User tolosa = verified("+251911000001", "Tolosa Bekele", Role.FARMER);
        farmers.updateMine(tolosa.getId(), new UpdateFarmerProfileRequest(FarmerType.COOPERATIVE, "Meki Fruit Co-op", 48,
                address(oromia, "East Shewa", "Meki", "Meki", 8.15, 38.82), new BigDecimal("12.5"), true,
                new BigDecimal("5000"), "Irrigated tomato and onion growers near Lake Ziway.", null,
                PayoutMethod.TELEBIRR, "Tolosa Bekele", "0911000001", Set.of(product("tomato").getId(),
                product("onion").getId())));
        User ayantu = verified("+251911000002", "Ayantu Gemechu", Role.FARMER);
        farmers.updateMine(ayantu.getId(), new UpdateFarmerProfileRequest(FarmerType.INDIVIDUAL, "Ayantu Farm", null,
                address(oromia, "East Shewa", "Adami Tulu", "Ziway", 7.93, 38.72), new BigDecimal("3"), true,
                new BigDecimal("2000"), null, null, PayoutMethod.CBE_BIRR, "Ayantu Gemechu", "1000123456789",
                Set.of(product("onion").getId(), product("potato").getId())));

        User selam = verified("+251922000001", "Selam Mekonnen", Role.BUYER);
        buyers.update(selam.getId(), new UpdateBuyerProfileRequest(BuyerType.RESTAURANT, "Habesha Kitchen",
                "Selam Mekonnen", "0012345678", "AA/2019/1234",
                address(addis, "Bole", "Bole", "Addis Ababa", 8.99, 38.79), "Back gate, behind Edna Mall"));

        User abebe = verified("+251933000001", "Abebe Tadesse", Role.DRIVER);
        drivers.updateMine(abebe.getId(), new UpdateDriverProfileRequest("DL-2020-5521", LocalDate.now().plusYears(2),
                VehicleType.ISUZU_TRUCK, "3-A 41218 AA", "Isuzu FSR", 2019, new BigDecimal("5000"), false, oromia,
                PayoutMethod.TELEBIRR, "Abebe Tadesse", "0933000001"));
        drivers.setAvailability(abebe.getId(), DriverAvailability.AVAILABLE);

        listings.create(tolosa.getId(), listing("tomato", "Tomato Grade A", QualityGrade.A, "2400", "46.00", "100",
                "25 kg crates", Unit.KG, null, LocalDate.now().minusDays(1)));
        listings.create(tolosa.getId(), listing("onion", "Red Onion", QualityGrade.A, "600", "38.00", "50",
                "50 kg sacks", Unit.KG, null, LocalDate.now()));
        listings.create(ayantu.getId(), listing("onion", "Onion Grade B", QualityGrade.B, "900", "31.50", "100",
                "50 kg sacks", Unit.KG, null, LocalDate.now().plusDays(1)));
        listings.create(ayantu.getId(), listing("potato", "Potato", QualityGrade.A, "1500", "24.00", "100",
                "50 kg sacks", Unit.KG, null, LocalDate.now()));
        log.info("Demo accounts (password {}): farmer {} / {}, buyer {}, driver {}", DEMO_PASSWORD,
                "0911000001", "0911000002", "0922000001", "0933000001");
    }

    private User verified(String phone, String name, Role role) {
        User user = auth.createVerifiedUser(phone, name, role, DEMO_PASSWORD);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        return user;
    }

    private UUID region(String code) {
        return regions.findByCode(code).map(Region::getId).orElseThrow();
    }

    private Product product(String slug) {
        return products.findBySlug(slug).orElseThrow();
    }

    private static AddressDto address(UUID region, String zone, String woreda, String town, double lat, double lon) {
        return new AddressDto(region, null, zone, woreda, town, null, lat, lon);
    }

    private CreateListingRequest listing(String slug, String title, QualityGrade grade, String qty, String price,
                                         String min, String packaging, Unit unit, BigDecimal unitWeight,
                                         LocalDate from) {
        return new CreateListingRequest(product(slug).getId(), title, null, grade, null, packaging,
                LocalDate.now().minusDays(1), from, null, unit, unitWeight, new BigDecimal(qty), new BigDecimal(min),
                new BigDecimal(price), false, null, null, true);
    }
}
