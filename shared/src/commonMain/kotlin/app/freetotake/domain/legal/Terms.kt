// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.legal

/** One paragraph of the Terms; headings are the numbered section titles. */
data class TermsBlock(val text: String, val heading: Boolean = false)

/**
 * "Terms & Conditions" v1.1 from Figma "Log in/TERMSANDCONDITIONS.docx", verbatim.
 * Service name changed from "Free to Give" to "Free to Take" (product decision, v1.12.1).
 */
object Terms {
    val blocks: List<TermsBlock> = listOf(
        TermsBlock("Free to Take is operated by the Free to Take developer, referred to in these Terms as “we”, “us”, or “our”. By using Free to Take, you agree to these Terms.", heading = false),
        TermsBlock("1. What Free to Take is", heading = true),
        TermsBlock("Free to Take is a free service that helps people give unwanted items to other people for free. You can browse items, offer items, request items, arrange an approved pickup, communicate about that pickup, and rate completed exchanges.", heading = false),
        TermsBlock("2. Free service and optional donations", heading = true),
        TermsBlock("Free to Take is free to use. We do not charge users to browse, give, request, chat, arrange pickup, or rate items. Items listed through Free to Take must be given away for free. Users must not ask another user for payment, a deposit, a fee, a tip, a gift, or other compensation for an item listed as free.", heading = false),
        TermsBlock("Free to Take may provide an optional way for users to make a voluntary donation to support the development, maintenance, hosting, security, and improvement of the project. A donation is optional and is not required to use the service, receive an item, give an item, or obtain any particular feature or treatment.", heading = false),
        TermsBlock("3. Your account", heading = true),
        TermsBlock("You may browse public listings without an account where the app allows it. You need an account for features such as giving items, requesting items, managing listings, or using authenticated features. Keep your account information accurate and keep your login details secure. Do not impersonate another person or use someone else's account without permission.", heading = false),
        TermsBlock("4. Pickup location — you choose it", heading = true),
        TermsBlock("Free to Take does not use your device GPS to automatically determine or publish your pickup location. You choose and enter the pickup address or pickup point yourself when creating a giveaway. The location you enter is associated with your account and the relevant giveaway so the service can provide the pickup information to the people you authorize.", heading = false),
        TermsBlock("Do not enter an address or location that you do not have the right to share. Do not include unnecessary personal information in a pickup location or pickup instructions. Before a request is approved, the app may show only an approximate area. The exact pickup information is intended to be available only to the authorized participants after approval.", heading = false),
        TermsBlock("5. What data Free to Take uses", heading = true),
        TermsBlock("Free to Take is designed to avoid collecting your device location or maintaining a history of where you have been. If you enter a pickup address, that address is information you choose to provide to the service. Account information, listings, pickup information, requests, ratings, and messages may also be processed when needed to provide the service.", heading = false),
        TermsBlock("We do not promise that data can never be exposed, lost, corrupted, accessed without authorization, or affected by a security incident. We use reasonable technical and organisational measures appropriate to the service, but no online service can guarantee absolute security.", heading = false),
        TermsBlock("6. Your responsibility for information you provide", heading = true),
        TermsBlock("You are responsible for the information you enter or upload. Do not provide another person's private information unless you have a lawful reason and permission to do so. Do not upload identity documents, payment information, passwords, private keys, or other sensitive information unless the app specifically asks for it.", heading = false),
        TermsBlock("7. Giving an item", heading = true),
        TermsBlock("When you give an item, you must have the right to give it away. Describe it honestly and mention important defects, missing parts, or safety concerns. Photos and descriptions must not be misleading.", heading = false),
        TermsBlock("8. Items that are not allowed", heading = true),
        TermsBlock("Do not use Free to Take for illegal, stolen, dangerous, or prohibited items. This includes weapons, explosives, illegal drugs, controlled substances, hazardous materials, stolen property, counterfeit goods, payment or identity documents, passwords, financial information, and anything else prohibited by law or our safety rules.", heading = false),
        TermsBlock("9. Requesting an item", heading = true),
        TermsBlock("You may request an available item and select an available pickup time when offered. A request does not guarantee that you will receive the item. The person giving the item chooses which request to approve. For a single item, only one requester can normally be approved.", heading = false),
        TermsBlock("10. Pickup and safety", heading = true),
        TermsBlock("After a request is approved, the app may show the exact pickup information and open a temporary chat. Users are responsible for arranging and completing the pickup safely and legally. We recommend sensible safety precautions and avoiding unnecessary disclosure of personal information.", heading = false),
        TermsBlock("11. Chat", heading = true),
        TermsBlock("Chat is intended for arranging an approved pickup. Do not use it for harassment, threats, scams, illegal activity, or requests for payment. Chat may be deleted after a limited period according to our retention rules and applicable law.", heading = false),
        TermsBlock("12. Ratings", heading = true),
        TermsBlock("After a completed exchange, the giver and recipient may rate each other. Ratings must be honest and based on a real interaction. You must not manipulate ratings, create fake ratings, or threaten someone over a rating.", heading = false),
        TermsBlock("13. Listings and expiration", heading = true),
        TermsBlock("A giveaway normally remains active for 7 days. We may remind the giver before it expires. A listing can be renewed while allowed by the app, but its maximum lifecycle is 14 days. After that, it expires. If the item is still available, the giver must create a new listing.", heading = false),
        TermsBlock("14. Respect and prohibited behaviour", heading = true),
        TermsBlock("Do not use Free to Take to harass, threaten, scam, discriminate against, impersonate, or harm other people. Do not manipulate requests, approvals, ratings, or notifications. Do not try to break, attack, scrape, or bypass security of the app.", heading = false),
        TermsBlock("15. Removing content or accounts", heading = true),
        TermsBlock("We may remove listings, messages, or other content, or restrict or suspend an account, when reasonably necessary for safety, legal compliance, abuse prevention, security, or violation of these Terms.", heading = false),
        TermsBlock("16. Items and users are not guaranteed", heading = true),
        TermsBlock("We provide a platform for people to connect. We do not guarantee the quality, safety, condition, authenticity, ownership, or availability of an item, and we do not guarantee that another user will attend a pickup or behave as expected. Users are responsible for their own decisions and interactions.", heading = false),
        TermsBlock("17. Privacy and European data protection", heading = true),
        TermsBlock("We respect applicable data-protection law, including the EU General Data Protection Regulation (GDPR) where it applies. The separate Privacy Policy explains what personal data we process, why we process it, the legal bases used, how long information is retained, who may receive it, whether information is transferred internationally, and how you can exercise your rights.", heading = false),
        TermsBlock("Nothing in these Terms removes or limits rights that you have under applicable data-protection law. Depending on the circumstances, these rights may include access, correction, deletion, restriction of processing, objection, and data portability, as well as the right to complain to a competent data-protection authority.", heading = false),
        TermsBlock("We will not claim that we are exempt from GDPR merely because the service is free or because users enter their own information. Where Free to Take determines the purposes and means of processing personal data, applicable data-protection responsibilities may apply. Where third-party providers process data on our behalf, their role and obligations will be governed by applicable law and the relevant agreements.", heading = false),
        TermsBlock("If a security incident affects personal data and the law requires notification, we will follow the applicable legal requirements. We cannot contractually exclude mandatory statutory responsibilities or a user's mandatory legal rights.", heading = false),
        TermsBlock("18. Changes to the service", heading = true),
        TermsBlock("We may change, improve, suspend, or remove parts of Free to Take when necessary. We may also update these Terms. If important changes are made, we will provide notice where appropriate.", heading = false),
        TermsBlock("19. Contact", heading = true),
        TermsBlock("If you have a question, safety concern, complaint, privacy request, or legal request about Free to Take, use the contact method provided in the app or Privacy Policy.", heading = false),
        TermsBlock("20. Acceptance", heading = true),
        TermsBlock("By using Free to Take, you confirm that you have read and understood these Terms and agree to follow them.", heading = false),
        TermsBlock("Version 1.1", heading = false),
    )
}
