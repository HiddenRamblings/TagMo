# Utiliser le ChameleonUltra avec TagMo

Pousse un amiibo (NTAG215) depuis TagMo vers un slot d'émulation du ChameleonUltra, en BLE.

## Prérequis
- Un **ChameleonUltra** (firmware ≥ v2.1 testé), chargé et réveillé (il s'endort : appuie sur le bouton).
- Téléphone Android avec Bluetooth activé et les permissions BLE accordées à TagMo.
- (Pour le mode « random serial » uniquement) tes **clés amiibo chargées dans TagMo**.

## Pousser un amiibo
1. Ouvre l'écran **Bluetooth / GATT** de TagMo, lance le scan (loupe).
2. Sélectionne **« ChameleonUltra »** dans la liste (laisse le type sur *Autodetect*) → connecte.
   - Une fois connecté, l'en-tête affiche `ChameleonUltra (vX.Y)`.
3. **« Upload binary to GATT device »** → choisis l'amiibo → choisis le **slot (1–8)**.
4. La progression s'affiche bloc par bloc ; un message confirme l'envoi.
5. Le slot émule l'amiibo : un lecteur NFC tiers (Switch, etc.) le reconnaît.

### Raccourci depuis le navigateur d'amiibos
Sur un amiibo : **Export to GATT** → sélectionne le ChameleonUltra → le choix du slot s'ouvre
directement (pas besoin de re-choisir l'amiibo).

## Modes
- **Clone à l'identique** (défaut) : l'UID d'origine est conservé ; le dump est poussé tel quel.
- **Clone with random serial** (interrupteur) : TagMo génère un amiibo à **UID aléatoire**, re-signé
  avec **tes** clés, avant l'envoi. Nécessite les clés amiibo chargées dans TagMo.

## Dépannage
- *Il n'apparaît pas au scan* : réveille le Chameleon (bouton) et relance le scan.
- *« Timed out »* à la connexion : réveille-le et réessaie ; rapproche le téléphone.
- *« Charge tes clés amiibo… »* : le mode random serial exige les clés amiibo dans TagMo.
- *Taille de dump* : les variantes 532/540/572 octets sont normalisées automatiquement à 540.

> Périmètre & licences : voir `docs/09-licensing-scope.md`. Dumps personnels uniquement.
