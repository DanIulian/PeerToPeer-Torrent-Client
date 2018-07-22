package client;

import java.io.File;
import java.io.IOException;

/**
 * Clasa abstracta din care va mosteni clasa principala a clientului.
 * Este necesara pentru uniformizarea si simplificarea testarii temei de casa.
 * @author Florin Pop
 */
public abstract class AbstractClient {
    
    /**
     *  Constructor fara cod efectiv, specificat aici doar din motive de interfata.
     *  Un client concret va trebui sa ofere un constructor public cu aceiasi parametri.
     *
     *  @param clientIp Adresa IP pe care clientul asculta cereri de download de la
     *                  ceilalti clienti ("peers") din retea.
     *  @param clientPort Portul pe care clientul asculta cereri de download de le
     *                    ceilalti clienti ("peers") din retea.
     *  @param centralNodeIp Adresa IP pe care nodul central asculta cereri de publicare si de cautare
     *                       de fisiere din partea clientilor.
     *  @param centralNodePort Portul pe care nodul central asculta cereri de publicare si de cautare
     *                         de fisiere din partea clientilor. 
     */ 
	/*
    protected AbstractClient(String clientIp, int clientPort, String centralNodeIp, int centralNodePort) {
    }
    */

    /**
     *  Metoda de interfata a clientului, prin care i se cere sa fragmenteze un fisier si sa il
     *  publice la nodul central.
     *  <p>
     *  Metoda este blocanta: se intoarce numai dupa ce cererea de publicare a fost trimisa nodului central,
     *  sau daca a intervenit o eroare (caz in care se arunca o exceptie).
     *
     *  @param file Un fisier pe care clientul va aplica un algoritm de fragmentare
     *              si pe care il va publica printr-o cerere la nodul central. Fisierul trebuie sa
     *              fie accesibil clientului, altfel se va arunca o IOException.
     *  @throws IOException O instanta de IOException va fii aruncata, specificandu-se
     *                      un mesaj relevant si cauza (v. constructorul {@link Exception#Exception(String, Throwable)})
     *                      in cazurile in care:
     *                      <ul>
     *                          <li>fisierul indicat nu este accesibil (ex.: nu exista, nu sunt drepturi de acces etc.)
     *                          <li>o eroare de comunicatie a intervenit in timpul publicarii fisierului catre nodul central
     *                      </ul>
     */
    public abstract void publishFile(File file) throws IOException;

    /**
     *  Metoda de interfata a clientului prin care i se cere sa aduca din retea (de la alti clienti - "peers")
     *  un fisier cu un nume dat.
     *  <p>
     *  Metoda este blocanta: se intoarce numai dupa ce fragmentele de fisier au fost aduse local (posibil
     *  din surse multiple) si asamblate, iar fragmentele individuale au fost publicate la nodul central.
     *  <p>
     *  Orice eroare in pasii de mai sus (ex.: unul din fragmentele de fisier nu a putut fi publicat la
     *  central), mai putin situatia in care numele fisierului este incorect, determina intoarcerea imediata
     *  a metodei, cu aruncarea unei exceptii.
     *
     *  @param filename Numele prin care fisierul care trebuie adus local este (unic) identificat in retea.
     *
     *  @return Daca fisierul specificat exista in retea <b>si</b> a putut fi descarcat complet, atunci se intoarce
     *          o referinta la fisierul salvat local.
     *  @return Daca fisierul specificat nu exista in retea (nodul central nu cunoaste nici un client care sa
     *          fi publicat vreun fragment dintr-un fisier cu acest nume), atunci se intoarce <code>null</code>.
     *
     *  @throws IOException In cazul unei erori de comunicatie intervenita in etapa:
     *                      <ul>
     *                          <li>publicarii unui fragment de fisier adus local
     *                          <li>descarcarii unui fragment de fisier de la unul din clienti
     *                          <li>comunicatiei cu nodul central
     *                      </ul>,
     *                      se arunca o IOException (exacta sau o subclasa a acesteia), in care se vor specifica
     *                      un mesaj clar de eroare si, eventual, cauza exceptiei (v. constructorul
     *                      {@link Exception#Exception(String, Throwable)} pentru detalii)
     *  
     */
    public abstract File retrieveFile(String filename) throws IOException;
} // class AbstractClient